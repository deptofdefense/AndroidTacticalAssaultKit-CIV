
#include <algorithm>
#include <deque>
#include "formats/cesium3dtiles/B3DM.h"
#include "formats/gltf/GLTF.h"
#include "util/Memory.h"
#include "math/Matrix2.h"
#include "model/SceneInfo.h"
#include "port/STLVectorAdapter.h"
#include "core/ProjectionFactory3.h"
#include "math/Utils.h"
#include "model/SceneBuilder.h"
#include "model/MeshTransformer.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Formats::Cesium3DTiles;
using namespace TAK::Engine::Formats::GLTF;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Core;

// Use tinygltf's copy of JSON for Modern C++
#include <tinygltf/json.hpp>
using json = nlohmann::json;

namespace {
    class RewindDataInput2 : public DataInput2 {
    public:
        RewindDataInput2(DataInput2* input) NOTHROWS;

        TAKErr close() NOTHROWS override;
        TAKErr read(uint8_t* buf, std::size_t* numRead, const std::size_t len) NOTHROWS override;
        TAKErr readByte(uint8_t* value) NOTHROWS override;
        TAKErr skip(const std::size_t n) NOTHROWS override;
        int64_t length() const NOTHROWS override;

        TAKErr safe() NOTHROWS;
        TAKErr rewind(size_t count) NOTHROWS;
        size_t numRecorded() const NOTHROWS;
        TAKErr enableRewind(bool enabled) NOTHROWS;

    private:
        TAKErr readDirect(uint8_t* buf, std::size_t* numRead, const std::size_t len) NOTHROWS;
        DataInput2 *input;
        std::deque<uint8_t> rbuf;
        size_t pos;
        size_t len_;
        bool record;
    };

    class LimitDataInput2 : public DataInput2 {
    public:
        LimitDataInput2(DataInput2 *input, int64_t limit) NOTHROWS;

        TAKErr close() NOTHROWS override;
        TAKErr read(uint8_t* buf, std::size_t* numRead, const std::size_t len) NOTHROWS override;
        TAKErr readByte(uint8_t* value) NOTHROWS override;
        TAKErr skip(const std::size_t n) NOTHROWS override;
        int64_t length() const NOTHROWS override;
    private:
        DataInput2* input;
        size_t left;
        size_t limit;
    };

    // Adapt to DataInput2 to std::streambuf
    class DataInput2Streambuf : public std::streambuf {
    public:
        DataInput2Streambuf(TAK::Engine::Util::DataInput2* input);

        int_type underflow() override;

    private:
        TAK::Engine::Util::DataInput2* input;
        char curr;
    };

    class B3DMRootSceneNode : public SceneNode {
    public:
        B3DMRootSceneNode(ScenePtr&& glTFScene, const Point2<double> &rtcCenter) NOTHROWS;
        ~B3DMRootSceneNode() override;
        bool isRoot() const NOTHROWS override;
        TAKErr getParent(const SceneNode** value) const NOTHROWS override;
        const Matrix2* getLocalFrame() const NOTHROWS override;
        TAKErr getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr& value) const NOTHROWS override;
        bool hasChildren() const NOTHROWS override;
        bool hasMesh() const NOTHROWS override;
        const Envelope2& getAABB() const NOTHROWS override;
        std::size_t getNumLODs() const NOTHROWS override;
        TAKErr loadMesh(std::shared_ptr<const Mesh>& value, const std::size_t lodIdx = 0u, ProcessingCallback* callback = nullptr) NOTHROWS override;
        TAKErr getLevelOfDetail(std::size_t* value, const std::size_t lodIdx) const NOTHROWS override;
        TAKErr getLODIndex(std::size_t* value, const double clod, const int round = 0) const NOTHROWS override;
        TAKErr getInstanceID(std::size_t* instanceId, const std::size_t lodIdx) const NOTHROWS override;
        bool hasSubscene() const NOTHROWS override;
        TAKErr getSubsceneInfo(const SceneInfo** result) NOTHROWS override;
        bool hasLODNode() const NOTHROWS override;
        TAKErr getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS override;

    private:
        ScenePtr glTFScene;
        Matrix2 localFrame;
    };

    class B3DMScene : public Scene {
    public:
        B3DMScene(ScenePtr &&glTFScene, const Point2<double>& rtcCenter) NOTHROWS;
        ~B3DMScene() NOTHROWS override;
        SceneNode& getRootNode() const NOTHROWS override;
        const Envelope2& getAABB() const NOTHROWS override;
        unsigned int getProperties() const NOTHROWS override;

    private:
        B3DMRootSceneNode root;
    };

    struct ParseData {
        ParseData() : glTFScene(nullptr, nullptr)
        {}
        ScenePtr glTFScene;
        json featureTableJSON;
        json batchTableJSON;
        Point2<double> rtcCenter;
    };

    TAKErr parse20ByteHeaderVersion(ParseData *result, RewindDataInput2* input) NOTHROWS;
    TAKErr parse24ByteHeaderVersion(ParseData *result, RewindDataInput2* input) NOTHROWS;
    TAKErr parse28ByteHeaderVersion(ParseData *result, RewindDataInput2* input) NOTHROWS;
    TAKErr parseFeatureTable(ParseData *result, DataInput2 *input,
        size_t jsonLength, size_t binaryLength) NOTHROWS;
    TAKErr parseBatchTable(ParseData* result, DataInput2* input,
        size_t jsonLength, size_t binaryLength) NOTHROWS;
}

const Matrix2 Y_UP_TO_Z_UP(
    1.0, 0.0, 0.0, 0.0,
    0.0, 0.0, -1.0, 0.0,
    0.0, 1.0, 0.0, 0.0,
    0.0, 0.0, 0.0, 1.0);


Matrix2 rootTransform(Point2<double> rtcCenter) {
    Matrix2 result;
    result.translate(rtcCenter.x, rtcCenter.y, rtcCenter.z);
    result.concatenate(Y_UP_TO_Z_UP);
    return result;
}

#define READ_UINT(v) \
    code = input->readInt((int32_t *)&v); \
    if (code != TE_Ok) \
        return code;

#define READ_CHAR(v) \
    code = input->readByte((uint8_t *)&v); \
    if (code != TE_Ok) \
        return code;

TAKErr parseImpl(ParseData *impl, bool fullParse, DataInput2* innerInput, const char* baseURI) NOTHROWS {

    if (!innerInput)
        return TE_InvalidArg;

    RewindDataInput2 rewindInput(innerInput);
    rewindInput.enableRewind(false);

    TAKErr code = TE_Ok;

    size_t numRead = 0;
    uint8_t magic[4] = { 0, 0, 0, 0 };

    code = rewindInput.read(magic, &numRead, 4);
    if (code != TE_Ok)
        return code;
    
    if (magic[0] != 'b' || magic[1] != '3' ||
        magic[2] != 'd' || magic[3] != 'm')
        return TE_InvalidArg;

    uint32_t version = 0;
    uint32_t byteLength = 0;

    DataInput2 *input = &rewindInput;
    READ_UINT(version);
    READ_UINT(byteLength);

#if 1
    rewindInput.enableRewind(true);

    code = parse20ByteHeaderVersion(impl, &rewindInput);
    if (code == TE_Unsupported) {
        rewindInput.rewind(rewindInput.numRecorded());
        code = parse24ByteHeaderVersion(impl, &rewindInput);
    }
    if (code == TE_Unsupported) {
        rewindInput.rewind(rewindInput.numRecorded());
        rewindInput.enableRewind(false);
        code = parse28ByteHeaderVersion(impl, &rewindInput);
    }
#else
    code = parse28ByteHeaderVersion(impl, &rewindInput);
#endif

    if (code != TE_Ok)
        return code;

    auto rtcCenter = impl->featureTableJSON.find("RTC_CENTER");
    if (rtcCenter != impl->featureTableJSON.end() && rtcCenter->is_array() && rtcCenter->size() == 3) {
        impl->rtcCenter.x = (*rtcCenter)[0].get<double>();
        impl->rtcCenter.y = (*rtcCenter)[1].get<double>();
        impl->rtcCenter.z = (*rtcCenter)[2].get<double>();
    }

    if (fullParse) {
        code = GLTF_load(impl->glTFScene, &rewindInput, baseURI);
        if (code != TE_Ok)
            return code;

        impl->glTFScene = ScenePtr(new B3DMScene(std::move(impl->glTFScene), impl->rtcCenter), Memory_deleter_const<Scene, B3DMScene>);
    }
    
    return code;
}


TAKErr TAK::Engine::Formats::Cesium3DTiles::B3DM_parse(ScenePtr& result, DataInput2* input, const char* baseURI) NOTHROWS {
    ParseData data;
    TAKErr code = parseImpl(&data, true, input, baseURI);
    if (code != TE_Ok)
        return code;
    result = std::move(data.glTFScene);
    return TE_Ok;
}

TAKErr TAK::Engine::Formats::Cesium3DTiles::B3DM_parseInfo(B3DMInfo* info, DataInput2* input, const char* baseURI) NOTHROWS {

    if (!info)
        return TE_InvalidArg;

    ParseData data;
    TAKErr code = parseImpl(&data, false, input, baseURI);
    if (code != TE_Ok)
        return code;

    info->rtcCenter = data.rtcCenter;

    return TE_Ok;
}

namespace {
    RewindDataInput2::RewindDataInput2(DataInput2* input) NOTHROWS
        : input(input),
        pos(0),
        len_(input ? static_cast<size_t>(input->length()) : 0),
        record(true)
    {}

    TAKErr RewindDataInput2::close() NOTHROWS {
        this->safe();
        return input->close();
    }

    TAKErr RewindDataInput2::read(uint8_t* buf, std::size_t* numRead, const std::size_t len) NOTHROWS {
        
        size_t localNumRead = 0;
        TAKErr retv = TE_IO;
        
        if (pos < rbuf.size()) {
            size_t avail = rbuf.size() - pos;
            size_t step = avail < len ? avail : len;
            std::copy_n(rbuf.begin() + pos, step, buf);
            pos += step;
            size_t directRead = 0;
            TAKErr code = readDirect(buf + step, &directRead, len - step);
            localNumRead = step + directRead;
            retv = code;
        } else {
            retv = readDirect(buf, &localNumRead, len);
        }

        if (numRead)
            *numRead = localNumRead;

        len_ -= localNumRead;
        return retv;
    }

    TAKErr RewindDataInput2::readByte(uint8_t* value) NOTHROWS {
        size_t numRead = 0;
        return read(value, &numRead, 1);
    }

    TAKErr RewindDataInput2::skip(const std::size_t n) NOTHROWS {
        
        TAKErr code = TE_Ok;
        size_t bufferSkip = std::min(rbuf.size() - pos, n);
        size_t directSkip = n - bufferSkip;

        if (directSkip > 0) {
            if (record) {
                rbuf.resize(pos + bufferSkip + directSkip);
                size_t numRead = 0;
                code = input->read(&rbuf[pos + bufferSkip], &numRead, directSkip);
                if (numRead != directSkip) {
                    rbuf.resize(pos + bufferSkip + numRead);
                    code = TE_InvalidArg;
                }
                if (code == TE_Ok)
                    pos += bufferSkip + numRead;
            } else {
                if (rbuf.size())
                    safe();
                code = input->skip(directSkip);
            }
        } else {
            pos += bufferSkip;
            if (rbuf.size() && !record)
                safe();
        }
        
        return code;
    }

    int64_t RewindDataInput2::length() const NOTHROWS {
        return len_;
    }

    TAKErr RewindDataInput2::safe() NOTHROWS {
        rbuf.erase(rbuf.begin(), rbuf.begin() + pos);
        pos = 0;
        return TE_Ok;
    }

    TAKErr RewindDataInput2::rewind(size_t count) NOTHROWS {
        if (count > pos)
            return TE_InvalidArg;
        pos -= count;
        len_ += count;
        return TE_Ok;
    }

    TAKErr RewindDataInput2::readDirect(uint8_t* buf, std::size_t* numRead, const std::size_t len) NOTHROWS {

        size_t recordCount = 0;
        TAKErr code = input->read(buf, &recordCount, len);
        if (recordCount && record) {
            rbuf.insert(rbuf.end(), buf, buf + recordCount);
            pos += recordCount;
        }

        if (!record && rbuf.size())
            safe();

        if (numRead && code == TE_Ok)
            *numRead = recordCount;

        return code;
    }

    size_t RewindDataInput2::numRecorded() const NOTHROWS {
        return pos;
    }

    TAKErr RewindDataInput2::enableRewind(bool enabled) NOTHROWS {
        this->record = enabled;
        return TE_Ok;
    }

    //
    // DataInput2Streambuf
    //

    DataInput2Streambuf::DataInput2Streambuf(TAK::Engine::Util::DataInput2* input)
        : input(input),
        curr(std::char_traits<char>::eof()) {}

    DataInput2Streambuf::int_type DataInput2Streambuf::underflow() {
        size_t nr = 0;
        char ch;
        input->read((uint8_t*)&ch, &nr, 1);
        if (nr == 1) {
            curr = ch;
            setg(&curr, &curr, &curr);
            return std::char_traits<char>::to_int_type(static_cast<char>(curr));
        }
        return std::char_traits<char>::eof();
    }

    //
    // LimitDataInput2
    //

    LimitDataInput2::LimitDataInput2(DataInput2* input, int64_t limit) NOTHROWS
        : input(input),
        left(static_cast<size_t>(limit)),
        limit(static_cast<size_t>(limit)) {}

    TAKErr LimitDataInput2::close() NOTHROWS {
        return input->close();
    }

    TAKErr LimitDataInput2::read(uint8_t* buf, std::size_t* numRead, const std::size_t len) NOTHROWS {
    
        if (left == 0)
            return TE_EOF;
        
        size_t limitLen = std::min(len, left);
        size_t numReadValue = 0;
        TAKErr code = input->read(buf, &numReadValue, limitLen);
        left -= numReadValue;
        if (numRead)
            *numRead = numReadValue;

        return code;
    }

    TAKErr LimitDataInput2::readByte(uint8_t* value) NOTHROWS {
        size_t numRead = 0;
        return read(value, &numRead, 1);
    }

    TAKErr LimitDataInput2::skip(const std::size_t n) NOTHROWS {

        if (n > left)
            return TE_InvalidArg;

        TAKErr code = input->skip(n);
        if (code == TE_Ok)
            left -= n;
        return code;
    }

    int64_t LimitDataInput2::length() const NOTHROWS {
        return limit;
    }

    //
    // B3DMRootSceneNode
    //

    B3DMRootSceneNode::B3DMRootSceneNode(ScenePtr&& glTFScene, const Point2<double>& rtcCenter) NOTHROWS
        : glTFScene(std::move(glTFScene))
    {
        localFrame.translate(rtcCenter.x, rtcCenter.y, rtcCenter.z);
        localFrame.concatenate(Y_UP_TO_Z_UP);
        if (this->glTFScene->getRootNode().getLocalFrame())
            localFrame.concatenate(*this->glTFScene->getRootNode().getLocalFrame());
    }
    
    B3DMRootSceneNode::~B3DMRootSceneNode()
    {}
    
    bool B3DMRootSceneNode::isRoot() const NOTHROWS {
        return true;
    }
    
    TAKErr B3DMRootSceneNode::getParent(const SceneNode** value) const NOTHROWS {
        *value = nullptr;
        return TE_Ok;
    }
    
    const Matrix2* B3DMRootSceneNode::getLocalFrame() const NOTHROWS {
        return &localFrame;
    }
    
    TAKErr B3DMRootSceneNode::getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr& value) const NOTHROWS {
        return glTFScene->getRootNode().getChildren(value);
    }
    
    bool B3DMRootSceneNode::hasChildren() const NOTHROWS {
        return glTFScene->getRootNode().hasChildren();
    }
    
    bool B3DMRootSceneNode::hasMesh() const NOTHROWS {
        return glTFScene->getRootNode().hasMesh();
    }
    
    const Envelope2& B3DMRootSceneNode::getAABB() const NOTHROWS {
        return glTFScene->getRootNode().getAABB();
    }
    
    std::size_t B3DMRootSceneNode::getNumLODs() const NOTHROWS {
        return glTFScene->getRootNode().getNumLODs();
    }
    
    TAKErr B3DMRootSceneNode::loadMesh(std::shared_ptr<const Mesh>& value, const std::size_t lodIdx, ProcessingCallback* callback) NOTHROWS {
        return glTFScene->getRootNode().loadMesh(value, lodIdx, callback);
    }
    
    TAKErr B3DMRootSceneNode::getLevelOfDetail(std::size_t* value, const std::size_t lodIdx) const NOTHROWS {
        return glTFScene->getRootNode().getLevelOfDetail(value, lodIdx);
    }
    
    TAKErr B3DMRootSceneNode::getLODIndex(std::size_t* value, const double clod, const int round) const NOTHROWS {
        return glTFScene->getRootNode().getLODIndex(value, clod, round);
    }
    
    TAKErr B3DMRootSceneNode::getInstanceID(std::size_t* instanceId, const std::size_t lodIdx) const NOTHROWS {
        return glTFScene->getRootNode().getInstanceID(instanceId, lodIdx);
    }
    
    bool B3DMRootSceneNode::hasSubscene() const NOTHROWS {
        return glTFScene->getRootNode().hasSubscene();
    }
    
    TAKErr B3DMRootSceneNode::getSubsceneInfo(const SceneInfo** result) NOTHROWS {
        return glTFScene->getRootNode().getSubsceneInfo(result);
    }

    bool B3DMRootSceneNode::hasLODNode() const NOTHROWS {
        return false;
    }

    TAKErr B3DMRootSceneNode::getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS {
        return TE_Unsupported;
    }

    //
    // B3DMScene
    //

    B3DMScene::B3DMScene(ScenePtr&& glTFScene, const Point2<double>& rtcCenter) NOTHROWS
        : root(std::move(glTFScene), rtcCenter)
    {}

    B3DMScene::~B3DMScene() NOTHROWS
    {}

    SceneNode& B3DMScene::getRootNode() const NOTHROWS {
        return const_cast<B3DMScene *>(this)->root;
    }

    const Envelope2& B3DMScene::getAABB() const NOTHROWS {
        return root.getAABB();
    }

    unsigned int B3DMScene::getProperties() const NOTHROWS {
        return DirectSceneGraph | DirectMesh;
    }

    //
    // impl
    //

    TAKErr parse20ByteHeaderVersion(ParseData* result, RewindDataInput2* input) NOTHROWS {

        TAKErr code = TE_Ok;
        uint32_t batchLength = 0;
        uint32_t batchTableByteLength = 0;

        READ_UINT(batchLength);
        READ_UINT(batchTableByteLength);

        if (batchTableByteLength > 0) {

            // check for start of JSON
            char jsonChar = 0;
            READ_CHAR(jsonChar);
            if (jsonChar != '{') {
                return TE_Unsupported;
            }
            input->rewind(1);
            input->enableRewind(false);

            code = parseBatchTable(result, input, batchTableByteLength, 0);
            if (code != TE_Ok)
                return code;

        } else {
            uint8_t magic[4] = { 0, 0, 0, 0 };
            code = input->read(magic, nullptr, 4);
            if (code != TE_Ok)
                return code;

            if (magic[0] != 'g' || magic[1] != 'l' ||
                magic[2] != 't' || magic[3] != 'f')
                return TE_Unsupported;
        }

        return code;
    }

    TAKErr parse24ByteHeaderVersion(ParseData* result, RewindDataInput2* input) NOTHROWS {

        TAKErr code = TE_Ok;
        uint32_t batchTableJSONByteLength = 0;
        uint32_t batchTableBinaryByteLength = 0;
        uint32_t batchLength = 0;

        READ_UINT(batchTableJSONByteLength);
        READ_UINT(batchTableBinaryByteLength);
        READ_UINT(batchLength);

        if (batchTableJSONByteLength > 0) {

            // check for start of JSON
            char jsonChar = 0;
            READ_CHAR(jsonChar);
            if (jsonChar != '{') {
                return TE_Unsupported;
            }
            input->rewind(1);
            input->enableRewind(false);

            code = parseBatchTable(result, input, batchTableJSONByteLength, batchTableBinaryByteLength);
            if (code != TE_Ok)
                return code;
        }

        return code;
    }

    TAKErr parse28ByteHeaderVersion(ParseData* result, RewindDataInput2* input) NOTHROWS {

        TAKErr code = TE_Ok;
        uint32_t featureTableJSONByteLength = 0;
        uint32_t featureTableBinaryByteLength = 0;
        uint32_t batchTableJSONByteLength = 0;
        uint32_t batchTableBinaryByteLength = 0;

        READ_UINT(featureTableJSONByteLength);
        READ_UINT(featureTableBinaryByteLength);
        READ_UINT(batchTableJSONByteLength);
        READ_UINT(batchTableBinaryByteLength);

        code = parseFeatureTable(result, input, featureTableJSONByteLength, featureTableBinaryByteLength);
        if (code != TE_Ok)
            return code;

        code = parseBatchTable(result, input, batchTableJSONByteLength, batchTableBinaryByteLength);
        if (code != TE_Ok)
            return code;

        return code;
    }

    TAKErr parseJSON(json* result, DataInput2* input) NOTHROWS {
        DataInput2Streambuf buf(input);
        std::istream in(&buf);
        *result = json::parse(in, nullptr, false);
        if (result->is_discarded())
            return TE_Err;
        return TE_Ok;
    }

    TAKErr parseFeatureTable(ParseData* result, DataInput2* input, size_t jsonLength, size_t binaryLength) NOTHROWS {

        TAKErr code = TE_Ok;

        if (jsonLength) {
            LimitDataInput2 jsonInput(input, jsonLength);
            code = parseJSON(&result->featureTableJSON, &jsonInput);
            TE_CHECKRETURN_CODE(code);
        }

        // skip binary for now
        // TODO-- handle properly
        code = input->skip(binaryLength);
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    TAKErr parseBatchTable(ParseData* result, DataInput2* input, size_t jsonLength, size_t binaryLength) NOTHROWS {
       
        TAKErr code = TE_Ok;

        if (jsonLength) {
            LimitDataInput2 jsonInput(input, jsonLength);
            code = parseJSON(&result->batchTableJSON, &jsonInput);
            TE_CHECKRETURN_CODE(code);
        }

        // skip binary for now
        // TODO-- handle properly
        code = input->skip(binaryLength);
        TE_CHECKRETURN_CODE(code);

        return TE_Ok;
    }
}