#include "cloudio_cli.h"
#include "cloudio.h"
#include "commoimpl.h"
#include "commoresult.h"
#include "simplefileioimpl.h"

#include <msclr/marshal.h>

using namespace TAK::Commo::impl;
using namespace TAK::Commo;
using namespace System;


CloudCollectionEntry::CloudCollectionEntry(Type t, System::String ^path,
    System::Int64 size) : path(path), type(t), size(size)
{
}

CloudCollectionEntry::~CloudCollectionEntry()
{
}


CloudIOUpdate::CloudIOUpdate(CloudIOOperation op,
    int transferId, SimpleFileIOStatus status,
    System::String ^info,
    System::Int64 bytesTransferred,
    System::Int64 totalBytes,
    array<CloudCollectionEntry ^> ^entries) :
    SimpleFileIOUpdate(transferId,
        status, info, bytesTransferred, totalBytes),
    operation(op), entries(entries)
{
}

CloudIOUpdate::~CloudIOUpdate()
{
}


CloudClient::CloudClient(atakmap::commoncommo::CloudClient *impl) : impl(impl)
{

}

CloudClient::~CloudClient()
{
    impl = NULL;
}

CommoResult CloudClient::testServerInit(int %cloudOpId)
{
    int id;
    atakmap::commoncommo::CommoResult r = impl->testServerInit(&id);
    if (r == atakmap::commoncommo::COMMO_SUCCESS) {
        cloudOpId = id;
        return CommoResult::CommoSuccess;
    } else
        return impl::nativeToCLI(r);
}

CommoResult CloudClient::listCollectionInit(int %cloudOpId, 
            System::String ^remotePath)
{
    msclr::interop::marshal_context mctx;
    const char *pathNative = mctx.marshal_as<const char *>(remotePath);

    int id;
    atakmap::commoncommo::CommoResult r = impl->listCollectionInit(&id, pathNative);
    if (r == atakmap::commoncommo::COMMO_SUCCESS) {
        cloudOpId = id;
        return CommoResult::CommoSuccess;
    } else
        return impl::nativeToCLI(r);
}

CommoResult CloudClient::getFileInit(int %cloudOpId, System::String ^localFile,
            System::String ^remotePath)
{
    msclr::interop::marshal_context mctx;
    const char *pathNative = mctx.marshal_as<const char *>(remotePath);
    const char *fileNative = mctx.marshal_as<const char *>(localFile);

    int id;
    atakmap::commoncommo::CommoResult r = impl->getFileInit(&id, 
                                                    fileNative, pathNative);
    if (r == atakmap::commoncommo::COMMO_SUCCESS) {
        cloudOpId = id;
        return CommoResult::CommoSuccess;
    } else
        return impl::nativeToCLI(r);
}

CommoResult CloudClient::putFileInit(int %cloudOpId,
            System::String ^remotePath, System::String ^localFile)
{
    msclr::interop::marshal_context mctx;
    const char *pathNative = mctx.marshal_as<const char *>(remotePath);
    const char *fileNative = mctx.marshal_as<const char *>(localFile);

    int id;
    atakmap::commoncommo::CommoResult r = impl->putFileInit(&id, pathNative,
                                                            fileNative);
    if (r == atakmap::commoncommo::COMMO_SUCCESS) {
        cloudOpId = id;
        return CommoResult::CommoSuccess;
    } else
        return impl::nativeToCLI(r);
}

CommoResult CloudClient::moveResourceInit(int %cloudOpId,
            System::String ^fromPath, System::String ^toPath)
{
    msclr::interop::marshal_context mctx;
    const char *fromNative = mctx.marshal_as<const char *>(fromPath);
    const char *toNative = mctx.marshal_as<const char *>(toPath);

    int id;
    atakmap::commoncommo::CommoResult r = impl->moveResourceInit(&id,
                                                        fromNative, toNative);
    if (r == atakmap::commoncommo::COMMO_SUCCESS) {
        cloudOpId = id;
        return CommoResult::CommoSuccess;
    } else
        return impl::nativeToCLI(r);
}

CommoResult CloudClient::createCollectionInit(int %cloudOpId,
            System::String ^remotePath)
{
    msclr::interop::marshal_context mctx;
    const char *pathNative = mctx.marshal_as<const char *>(remotePath);

    int id;
    atakmap::commoncommo::CommoResult r = impl->createCollectionInit(&id,
                                                            pathNative);
    if (r == atakmap::commoncommo::COMMO_SUCCESS) {
        cloudOpId = id;
        return CommoResult::CommoSuccess;
    } else
        return impl::nativeToCLI(r);
}

CommoResult CloudClient::startOperation(int cloudOpId)
{
    atakmap::commoncommo::CommoResult r = impl->startOperation(cloudOpId);
    return impl::nativeToCLI(r);
}

CommoResult CloudClient::cancelOperation(int cloudOpId)
{
    impl->cancelOperation(cloudOpId);
    return CommoResult::CommoSuccess;
}
