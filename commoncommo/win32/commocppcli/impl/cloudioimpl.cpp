#include "cloudioimpl.h"
#include "simplefileioimpl.h"

using namespace TAK::Commo;
using namespace System;

impl::CloudIOImpl::CloudIOImpl(ICloudIO ^io) : atakmap::commoncommo::CloudIO(),
    ioCLI(io)
{
}
impl::CloudIOImpl::~CloudIOImpl() {}

void impl::CloudIOImpl::cloudOperationUpdate(const atakmap::commoncommo::CloudIOUpdate *update)
{
    String ^infoString = nullptr;
    if (update->additionalInfo)
        infoString = gcnew String(update->additionalInfo, 0, (int)strlen(update->additionalInfo), System::Text::Encoding::UTF8);
    int64_t bt = (int64_t)update->bytesTransferred;
    if (update->bytesTransferred > INT64_MAX)
        bt = INT64_MAX;
    int64_t tbt = (int64_t)update->totalBytesToTransfer;
    if (update->totalBytesToTransfer > INT64_MAX)
        tbt = INT64_MAX;

    array<CloudCollectionEntry ^> ^entriesCLI = nullptr;
    if (update->operation == atakmap::commoncommo::CloudIOOperation::CLOUDIO_OP_LIST_COLLECTION &&
        update->status == atakmap::commoncommo::SimpleFileIOStatus::FILEIO_SUCCESS) {
        entriesCLI = gcnew array<CloudCollectionEntry ^>(update->numEntries);
        for (size_t i = 0; i < update->numEntries; ++i) {
            const atakmap::commoncommo::CloudCollectionEntry *e = update->entries[i];
            entriesCLI[i] = gcnew CloudCollectionEntry(nativeToCLI(e->type), gcnew String(e->path), e->fileSize);
        }
    }

    CloudIOUpdate ^up = gcnew CloudIOUpdate(
        nativeToCLI(update->operation),
        update->xferid,
        SimpleFileIOImpl::nativeToCLI(update->status),
        gcnew System::String(update->additionalInfo),
        bt, tbt, entriesCLI);

    ioCLI->cloudOperationUpdate(up);
}

CloudIOOperation impl::CloudIOImpl::nativeToCLI(atakmap::commoncommo::CloudIOOperation op)
{
    CloudIOOperation ret;
    switch (op) {
    case atakmap::commoncommo::CloudIOOperation::CLOUDIO_OP_GET:
        ret = CloudIOOperation::Get;
        break;
    case atakmap::commoncommo::CloudIOOperation::CLOUDIO_OP_LIST_COLLECTION:
        ret = CloudIOOperation::ListCollection;
        break;
    case atakmap::commoncommo::CloudIOOperation::CLOUDIO_OP_MAKE_COLLECTION:
        ret = CloudIOOperation::MakeCollection;
        break;
    case atakmap::commoncommo::CloudIOOperation::CLOUDIO_OP_MOVE:
        ret = CloudIOOperation::Move;
        break;
    case atakmap::commoncommo::CloudIOOperation::CLOUDIO_OP_PUT:
        ret = CloudIOOperation::Put;
        break;
    case atakmap::commoncommo::CloudIOOperation::CLOUDIO_OP_TEST_SERVER:
        ret = CloudIOOperation::TestServer;
        break;
    }
    return ret;
}

CloudCollectionEntry::Type impl::CloudIOImpl::nativeToCLI(atakmap::commoncommo::CloudCollectionEntry::Type t)
{
    CloudCollectionEntry::Type ret;
    switch (t) {
    case atakmap::commoncommo::CloudCollectionEntry::Type::TYPE_COLLECTION:
        ret = CloudCollectionEntry::Type::Collection;
        break;
    case atakmap::commoncommo::CloudCollectionEntry::Type::TYPE_FILE:
        ret = CloudCollectionEntry::Type::File;
        break;
    }
    return ret;
}
