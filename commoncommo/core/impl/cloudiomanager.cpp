#include "cloudiomanager.h"
#include <string.h>

using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;

/***************************************************************************/
// Internal utils


namespace {
    const char *OWNCLOUD_WEBDAV_PATH = "/remote.php/webdav";
    const char *OWNCLOUD_CAPS_PATH = "/ocs/v1.php/cloud/capabilities";

    void removeExtraSlashes(std::string *s)
    {
        std::string::iterator iter = s->begin();
        bool sawSlash = false;
        while (iter != s->end()) {
            if (*iter == '/') {
                if (sawSlash) {
                    iter = s->erase(iter);
                    continue;
                } else {
                    sawSlash = true;
                }
            } else {
                sawSlash = false;
            }
            iter++;
        }
    }
}


/***************************************************************************/
// FTP parsing routines
// Based on D.J. Bernstein's ftpparse "library"
// http://cr.yp.to/ftpparse.html
// Version 20001223
// Changes: remove time handling as it isn't portable

namespace {

  struct ftpparse {
    const char *name; /* not necessarily 0-terminated */
    size_t namelen;
    int flagtrycwd; /* 0 if cwd is definitely pointless, 1 otherwise */
    int flagtryretr; /* 0 if retr is definitely pointless, 1 otherwise */
    int sizetype;
    size_t size; /* number of octets */
    int mtimetype;
    time_t mtime; /* modification time */
    int idtype;
    const char *id; /* not necessarily 0-terminated */
    size_t idlen;
  } ;

  #define FTPPARSE_SIZE_UNKNOWN 0
  #define FTPPARSE_SIZE_BINARY 1 /* size is the number of octets in TYPE I */
  #define FTPPARSE_SIZE_ASCII 2 /* size is the number of octets in TYPE A */

  #define FTPPARSE_MTIME_UNKNOWN 0
  #define FTPPARSE_MTIME_LOCAL 1 /* time is correct */
  #define FTPPARSE_MTIME_REMOTEMINUTE 2 /* time zone and secs are unknown */
  #define FTPPARSE_MTIME_REMOTEDAY 3 /* time zone and time of day are unknown */
  /*
  When a time zone is unknown, it is assumed to be GMT. You may want
  to use localtime() for LOCAL times, along with an indication that the
  time is correct in the local time zone, and gmtime() for REMOTE* times.
  */

  #define FTPPARSE_ID_UNKNOWN 0
  #define FTPPARSE_ID_FULL 1 /* unique identifier for files on this FTP server */

  /*
  ftpparse(&fp,buf,len) tries to parse one line of LIST output.

  The line is an array of len characters stored in buf.
  It should not include the terminating CR LF; so buf[len] is typically CR.

  If ftpparse() can't find a filename, it returns 0.

  If ftpparse() can find a filename, it fills in fp and returns 1.
  fp is a struct ftpparse, defined below.
  The name is an array of fp.namelen characters stored in fp.name;
  fp.name points somewhere within buf.
  */


  int check(const char *buf, const char *monthname)
  {
    if ((buf[0] != monthname[0]) && (buf[0] != monthname[0] - 32)) return 0;
    if ((buf[1] != monthname[1]) && (buf[1] != monthname[1] - 32)) return 0;
    if ((buf[2] != monthname[2]) && (buf[2] != monthname[2] - 32)) return 0;
    return 1;
  }

  const char *months[12] = {
    "jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec"
  } ;

  int getmonth(const char *buf, size_t len)
  {
    int i;
    if (len == 3)
      for (i = 0;i < 12;++i)
        if (check(buf,months[i])) return i;
    return -1;
  }

  long getlong(const char *buf,size_t len)
  {
    long u = 0;
    while (len-- > 0)
      u = u * 10 + (*buf++ - '0');
    return u;
  }

  int ftpparse(struct ftpparse *fp, const char *buf, size_t len)
  {
    size_t i;
    size_t j;
    int state;
    long size = 0;

    fp->name = 0;
    fp->namelen = 0;
    fp->flagtrycwd = 0;
    fp->flagtryretr = 0;
    fp->sizetype = FTPPARSE_SIZE_UNKNOWN;
    fp->size = 0;
    fp->mtimetype = FTPPARSE_MTIME_UNKNOWN;
    fp->mtime = 0;
    fp->idtype = FTPPARSE_ID_UNKNOWN;
    fp->id = 0;
    fp->idlen = 0;

    if (len < 2) /* an empty name in EPLF, with no info, could be 2 chars */
      return 0;

    switch(*buf) {
      /* see http://pobox.com/~djb/proto/eplf.txt */
      /* "+i8388621.29609,m824255902,/,\tdev" */
      /* "+i8388621.44468,m839956783,r,s10376,\tRFCEPLF" */
      case '+':
        i = 1;
        for (j = 1;j < len;++j) {
          if (buf[j] == 9) {
            fp->name = buf + j + 1;
            fp->namelen = len - j - 1;
            return 1;
          }
          if (buf[j] == ',') {
            switch(buf[i]) {
              case '/':
                fp->flagtrycwd = 1;
                break;
              case 'r':
                fp->flagtryretr = 1;
                break;
              case 's':
                fp->sizetype = FTPPARSE_SIZE_BINARY;
                fp->size = getlong(buf + i + 1,j - i - 1);
                break;
              case 'm':
                break;
              case 'i':
                fp->idtype = FTPPARSE_ID_FULL;
                fp->id = buf + i + 1;
                fp->idlen = j - i - 1;
            }
            i = j + 1;
          }
        }
        return 0;
      
      /* UNIX-style listing, without inum and without blocks */
      /* "-rw-r--r--   1 root     other        531 Jan 29 03:26 README" */
      /* "dr-xr-xr-x   2 root     other        512 Apr  8  1994 etc" */
      /* "dr-xr-xr-x   2 root     512 Apr  8  1994 etc" */
      /* "lrwxrwxrwx   1 root     other          7 Jan 25 00:17 bin -> usr/bin" */
      /* Also produced by Microsoft's FTP servers for Windows: */
      /* "----------   1 owner    group         1803128 Jul 10 10:18 ls-lR.Z" */
      /* "d---------   1 owner    group               0 May  9 19:45 Softlib" */
      /* Also WFTPD for MSDOS: */
      /* "-rwxrwxrwx   1 noone    nogroup      322 Aug 19  1996 message.ftp" */
      /* Also NetWare: */
      /* "d [R----F--] supervisor            512       Jan 16 18:53    login" */
      /* "- [R----F--] rhesus             214059       Oct 20 15:27    cx.exe" */
      /* Also NetPresenz for the Mac: */
      /* "-------r--         326  1391972  1392298 Nov 22  1995 MegaPhone.sit" */
      /* "drwxrwxr-x               folder        2 May 10  1996 network" */
      case 'b':
      case 'c':
      case 'd':
      case 'l':
      case 'p':
      case 's':
      case '-':

        if (*buf == 'd') fp->flagtrycwd = 1;
        if (*buf == '-') fp->flagtryretr = 1;
        if (*buf == 'l') fp->flagtrycwd = fp->flagtryretr = 1;

        state = 1;
        i = 0;
        for (j = 1;j < len;++j)
          if ((buf[j] == ' ') && (buf[j - 1] != ' ')) {
            switch(state) {
              case 1: /* skipping perm */
                state = 2;
                break;
              case 2: /* skipping nlink */
                state = 3;
                if ((j - i == 6) && (buf[i] == 'f')) /* for NetPresenz */
                  state = 4;
                break;
              case 3: /* skipping uid */
                state = 4;
                break;
              case 4: /* getting tentative size */
                size = getlong(buf + i,j - i);
                state = 5;
                break;
              case 5: /* searching for month, otherwise getting tentative size */
              {
                int month = getmonth(buf + i,j - i);
                if (month >= 0)
                  state = 6;
                else
                  size = getlong(buf + i,j - i);
                break;
              }
              case 6: /* have size and month */
                //mday = getlong(buf + i,j - i);
                state = 7;
                break;
              case 7: /* have size, month, mday */
                if ((j - i == 4) && (buf[i + 1] == ':')) {
  //                hour = getlong(buf + i,1);
  //                minute = getlong(buf + i + 2,2);
  //                fp->mtimetype = FTPPARSE_MTIME_REMOTEMINUTE;
  //                initbase();
  //                fp->mtime = base + guesstai(month,mday) + hour * 3600 + minute * 60;
                } else if ((j - i == 5) && (buf[i + 2] == ':')) {
  //                hour = getlong(buf + i,2);
  //                minute = getlong(buf + i + 3,2);
  //                fp->mtimetype = FTPPARSE_MTIME_REMOTEMINUTE;
  //                initbase();
  //                fp->mtime = base + guesstai(month,mday) + hour * 3600 + minute * 60;
                }
                else if (j - i >= 4) {
  //                year = getlong(buf + i,j - i);
  //                fp->mtimetype = FTPPARSE_MTIME_REMOTEDAY;
  //                initbase();
  //                fp->mtime = base + totai(year,month,mday);
                }
                else
                  return 0;
                fp->name = buf + j + 1;
                fp->namelen = len - j - 1;
                state = 8;
                break;
              case 8: /* twiddling thumbs */
                break;
            }
            i = j + 1;
            while ((i < len) && (buf[i] == ' ')) ++i;
          }

        if (state != 8)
          return 0;

        fp->size = size;
        fp->sizetype = FTPPARSE_SIZE_BINARY;

        if (*buf == 'l')
          for (i = 0;i + 3 < fp->namelen;++i)
            if (fp->name[i] == ' ')
              if (fp->name[i + 1] == '-')
                if (fp->name[i + 2] == '>')
                  if (fp->name[i + 3] == ' ') {
                    fp->namelen = i;
                    break;
                  }

        /* eliminate extra NetWare spaces */
        if ((buf[1] == ' ') || (buf[1] == '['))
          if (fp->namelen > 3)
            if (fp->name[0] == ' ')
              if (fp->name[1] == ' ')
                if (fp->name[2] == ' ') {
                  fp->name += 3;
                  fp->namelen -= 3;
                }

        return 1;
    }

    /* MultiNet (some spaces removed from examples) */
    /* "00README.TXT;1      2 30-DEC-1996 17:44 [SYSTEM] (RWED,RWED,RE,RE)" */
    /* "CORE.DIR;1          1  8-SEP-1996 16:09 [SYSTEM] (RWE,RWE,RE,RE)" */
    /* and non-MutliNet VMS: */
    /* "CII-MANUAL.TEX;1  213/216  29-JAN-1996 03:33:12  [ANONYMOU,ANONYMOUS]   (RWED,RWED,,)" */
    for (i = 0;i < len;++i)
      if (buf[i] == ';')
        break;
    if (i < len) {
      fp->name = buf;
      fp->namelen = i;
      if (i > 4)
        if (buf[i - 4] == '.')
          if (buf[i - 3] == 'D')
            if (buf[i - 2] == 'I')
              if (buf[i - 1] == 'R') {
                fp->namelen -= 4;
                fp->flagtrycwd = 1;
              }
      if (!fp->flagtrycwd)
        fp->flagtryretr = 1;
      while (buf[i] != ' ') if (++i == len) return 0;
      while (buf[i] == ' ') if (++i == len) return 0;
      while (buf[i] != ' ') if (++i == len) return 0;
      while (buf[i] == ' ') if (++i == len) return 0;
      j = i;
      while (buf[j] != '-') if (++j == len) return 0;
  //    mday = getlong(buf + i,j - i);
      while (buf[j] == '-') if (++j == len) return 0;
      i = j;
      while (buf[j] != '-') if (++j == len) return 0;
      int month = getmonth(buf + i,j - i);
      if (month < 0) return 0;
      while (buf[j] == '-') if (++j == len) return 0;
      i = j;
      while (buf[j] != ' ') if (++j == len) return 0;
  //    year = getlong(buf + i,j - i);
      while (buf[j] == ' ') if (++j == len) return 0;
      i = j;
      while (buf[j] != ':') if (++j == len) return 0;
  //    hour = getlong(buf + i,j - i);
      while (buf[j] == ':') if (++j == len) return 0;
      i = j;
      while ((buf[j] != ':') && (buf[j] != ' ')) if (++j == len) return 0;
  //    minute = getlong(buf + i,j - i);

  //    fp->mtimetype = FTPPARSE_MTIME_REMOTEMINUTE;
  //    initbase();
  //    fp->mtime = base + totai(year,month,mday) + hour * 3600 + minute * 60;

      return 1;
    }

    /* MSDOS format */
    /* 04-27-00  09:09PM       <DIR>          licensed */
    /* 07-18-00  10:16AM       <DIR>          pub */
    /* 04-14-00  03:47PM                  589 readme.htm */
    if ((*buf >= '0') && (*buf <= '9')) {
      i = 0;
      j = 0;
      while (buf[j] != '-') if (++j == len) return 0;
  //    month = getlong(buf + i,j - i) - 1;
      while (buf[j] == '-') if (++j == len) return 0;
      i = j;
      while (buf[j] != '-') if (++j == len) return 0;
  //    mday = getlong(buf + i,j - i);
      while (buf[j] == '-') if (++j == len) return 0;
      i = j;
      while (buf[j] != ' ') if (++j == len) return 0;
  //    year = getlong(buf + i,j - i);
  //    if (year < 50) year += 2000;
  //    if (year < 1000) year += 1900;
      while (buf[j] == ' ') if (++j == len) return 0;
      i = j;
      while (buf[j] != ':') if (++j == len) return 0;
  //    hour = getlong(buf + i,j - i);
      while (buf[j] == ':') if (++j == len) return 0;
      i = j;
      while ((buf[j] != 'A') && (buf[j] != 'P')) if (++j == len) return 0;
  //    minute = getlong(buf + i,j - i);
  //    if (hour == 12) hour = 0;
      if (buf[j] == 'A') if (++j == len) return 0;
      if (buf[j] == 'P') { /*hour += 12;*/ if (++j == len) return 0; }
      if (buf[j] == 'M') if (++j == len) return 0;

      while (buf[j] == ' ') if (++j == len) return 0;
      if (buf[j] == '<') {
        fp->flagtrycwd = 1;
        while (buf[j] != ' ') if (++j == len) return 0;
      }
      else {
        i = j;
        while (buf[j] != ' ') if (++j == len) return 0;
        fp->size = getlong(buf + i,j - i);
        fp->sizetype = FTPPARSE_SIZE_BINARY;
        fp->flagtryretr = 1;
      }
      while (buf[j] == ' ') if (++j == len) return 0;

      fp->name = buf + j;
      fp->namelen = len - j;

  //    fp->mtimetype = FTPPARSE_MTIME_REMOTEMINUTE;
  //    initbase();
  //    fp->mtime = base + totai(year,month,mday) + hour * 3600 + minute * 60;

      return 1;
    }

    /* Some useless lines, safely ignored: */
    /* "Total of 11 Files, 10966 Blocks." (VMS) */
    /* "total 14786" (UNIX) */
    /* "DISK$ANONFTP:[ANONYMOUS]" (VMS) */
    /* "Directory DISK$PCSA:[ANONYM]" (VMS) */

    return 0;
  }
}



/***************************************************************************/
// CloudIOManager


CloudIOManager::CloudIOManager(CommoLogger *logger,
                               URLRequestManager *urlManager) :
                                   logger(logger),
                                   urlManager(urlManager),
                                   clientsMutex(),
                                   clients()
{
}

CloudIOManager::~CloudIOManager()
{
    std::set<InternalCloudClient *>::iterator iter;
    for (iter = clients.begin(); iter != clients.end(); iter++) {
        InternalCloudClient *c = *iter;
        urlManager->cancelRequestsForIO(c);
        delete c;
    }
}

CommoResult CloudIOManager::createCloudClient(
                         CloudClient **result,
                         CloudIO *io,
                         CloudIOProtocol proto,
                         const char *host,
                         int port,
                         const char *basePath,
                         const char *user,
                         const char *pass,
                         const uint8_t *caCerts,
                         size_t caCertsLen,
                         const char *caCertPassword)
{
    try {
        InternalCloudClient *client;
        switch (proto) {
        case CLOUDIO_PROTO_HTTPS:
        case CLOUDIO_PROTO_HTTP:
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Creating owncloud client %s", host);
            client = new OwncloudClient(
                                this, io, proto, host, port, basePath,
                                user, pass, caCerts, caCertsLen,
                                caCertPassword);
            break;
        case CLOUDIO_PROTO_FTP:
        case CLOUDIO_PROTO_FTPS:
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Creating ftp client %s", host);
            client = new FTPCloudClient(
                                this, io, proto, host, port, basePath,
                                user, pass, caCerts, caCertsLen,
                                caCertPassword);
            break;
        default:
            throw COMMO_ILLEGAL_ARGUMENT;
            break;
        }
        

        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, clientsMutex);
            clients.insert(client);
        }
        *result = client;
        return COMMO_SUCCESS;
    } catch (CommoResult r) {
        return r;
    }
}

CommoResult CloudIOManager::destroyCloudClient(CloudClient *client)
{
    PGSC::Thread::LockPtr lock(NULL, NULL);
    PGSC::Thread::Lock_create(lock, clientsMutex);
    InternalCloudClient *iclient = (InternalCloudClient *)client;

    if (clients.erase(iclient) != 1)
        return COMMO_ILLEGAL_ARGUMENT;

    urlManager->cancelRequestsForIO(iclient);
    delete iclient;
    return COMMO_SUCCESS;
}


/***************************************************************************/
// InternalCloudClient - internals

InternalCloudClient::InternalCloudClient(
                  CloudIOManager *owner,
                  CloudIO *clientIO,
                  CloudIOProtocol proto,
                  const char *user,
                  const char *pass) COMMO_THROW (CommoResult) :
        CloudClient(), 
        owner(owner), clientIO(clientIO), proto(proto),
        caCerts(NULL), useLogin(false), user(""), pass("")
{
    if (!user && pass)
        throw COMMO_ILLEGAL_ARGUMENT;
    if (user) {
        this->user = user;
        useLogin = true;
    }
    if (pass)
        this->pass = pass;
}


InternalCloudClient::~InternalCloudClient()
{
    if (caCerts) {
        sk_X509_pop_free(caCerts, X509_free);
    }
}

void InternalCloudClient::parseCerts(const uint8_t *caCert,
                    const size_t caCertLen,
                    const char *caCertPassword) COMMO_THROW (CommoResult)
{
    try {
        int nCaCerts = 0;
        InternalUtils::readCACerts(caCert,
                                   caCertLen,
                                   caCertPassword,
                                   &caCerts,
                                   &nCaCerts);

    } catch (SSLArgException &e) {
        throw e.errCode;
    }
}

CommoResult InternalCloudClient::startOperation(int cloudIOid)
{
    return owner->urlManager->startTransfer(cloudIOid);
}

void InternalCloudClient::cancelOperation(int cloudIOid)
{
    owner->urlManager->cancelRequest(cloudIOid);
}


/***************************************************************************/
// FTPCloudClient - internals


FTPCloudClient::FTPCloudClient(
                  CloudIOManager *owner,
                  CloudIO *clientIO,
                  CloudIOProtocol proto,
                  const char *host,
                  int port,
                  const char *basePath,
                  const char *user,
                  const char *pass,
                  const uint8_t *caCert,
                  const size_t caCertLen,
                  const char *caCertPassword) COMMO_THROW (CommoResult) :
        InternalCloudClient(owner, clientIO, proto, user, pass), 
        isSSL(false),
        basePath(""),
        baseUrl("")
{
    switch (proto) {
    case CLOUDIO_PROTO_FTPS:
        isSSL = true;
        baseUrl = "ftps://";
        break;
    case CLOUDIO_PROTO_FTP:
        isSSL = false;
        baseUrl = "ftp://";
        break;
    default:
        throw COMMO_ILLEGAL_ARGUMENT;
    }
    std::string basePathTrimmed = basePath;
    // Always lead basePath with a / since client may not have included one
    // and if they did, our duplicate removal will take care of it
    basePathTrimmed = "/";
    basePathTrimmed += basePath;
    // Similar, always end with /
    basePathTrimmed += "/";
    removeExtraSlashes(&basePathTrimmed);

    this->basePath = basePathTrimmed;

    baseUrl += host;
    baseUrl += ":";
    baseUrl += InternalUtils::intToString(port);
    baseUrl += basePathTrimmed;

    if (isSSL && caCert && caCertLen) {
        parseCerts(caCert, caCertLen, caCertPassword);
    }
}

FTPCloudClient::~FTPCloudClient()
{
}

void FTPCloudClient::urlRequestUpdate(URLIOUpdate *update)
{
    const CloudIOUpdate *cup = (const CloudIOUpdate *)update->getBaseUpdate();
    clientIO->cloudOperationUpdate(cup);
}



/***************************************************************************/
// FTPCloudClient - public API

CommoResult FTPCloudClient::testServerInit(int *cloudIOid)
{
    FTPCloudURLRequest *r = new FTPCloudURLRequest(CLOUDIO_OP_TEST_SERVER,
                                 URLRequest::BUFFER_DOWNLOAD,
                                 baseUrl,
                                 "/",
                                 "",
                                 useLogin,
                                 user,
                                 pass,
                                 isSSL,
                                 caCerts);
    owner->urlManager->initRequest(cloudIOid, this, r);
    return COMMO_SUCCESS;
}

CommoResult FTPCloudClient::listCollectionInit(int *cloudIOid,
                                                    const char *path)
{
    std::string rpath = "/";
    rpath += path;
    // Put a / on the end in case the user didn't.
    // Having this on the end is necessary for curl to attempt
    // a listing versus a download
    // This way it is easy for us to append list results
    // to the path for a returned entry listing
    rpath += "/";
    removeExtraSlashes(&rpath);
    FTPCloudURLRequest *r = new FTPCloudURLRequest(CLOUDIO_OP_LIST_COLLECTION,
                                 URLRequest::BUFFER_DOWNLOAD,
                                 baseUrl,
                                 rpath,
                                 "",
                                 useLogin,
                                 user,
                                 pass,
                                 isSSL,
                                 caCerts);
    owner->urlManager->initRequest(cloudIOid, this, r);
    return COMMO_SUCCESS;
}

CommoResult FTPCloudClient::getFileInit(int *cloudIOid,
                                             const char *localFile,
                                             const char *remotePath)
{
    std::string rpath = "/";
    rpath += remotePath;
    removeExtraSlashes(&rpath);

    FTPCloudURLRequest *r = new FTPCloudURLRequest(CLOUDIO_OP_GET,
                                 URLRequest::FILE_DOWNLOAD,
                                 baseUrl,
                                 rpath,
                                 localFile,
                                 useLogin,
                                 user,
                                 pass,
                                 isSSL,
                                 caCerts);
    owner->urlManager->initRequest(cloudIOid, this, r);
    return COMMO_SUCCESS;
}
                                    
CommoResult FTPCloudClient::putFileInit(int *cloudIOid,
                                             const char *remotePath,
                                             const char *localFile)
{
    std::string rpath = "/";
    rpath += remotePath;
    removeExtraSlashes(&rpath);
    FTPCloudURLRequest *r = new FTPCloudURLRequest(CLOUDIO_OP_PUT,
                                 URLRequest::FILE_UPLOAD,
                                 baseUrl,
                                 rpath,
                                 localFile,
                                 useLogin,
                                 user,
                                 pass,
                                 isSSL,
                                 caCerts);
    owner->urlManager->initRequest(cloudIOid, this, r);
    return COMMO_SUCCESS;
}

CommoResult FTPCloudClient::moveResourceInit(int *cloudIOid,
                                                  const char *fromPath,
                                                  const char *toPath)
{
    std::string tpath = basePath;
    tpath += toPath;
    removeExtraSlashes(&tpath);
    std::string fpath = basePath;
    fpath += fromPath;
    removeExtraSlashes(&fpath);

    FTPCloudURLRequest *r = new FTPCloudURLRequest(CLOUDIO_OP_MOVE,
                                 URLRequest::BUFFER_DOWNLOAD,
                                 baseUrl,
                                 "/",
                                 "",
                                 useLogin,
                                 user,
                                 pass,
                                 isSSL,
                                 caCerts);
    r->setDestPath(tpath);
    r->setRenameSrcPath(fpath);
    owner->urlManager->initRequest(cloudIOid, this, r);
    return COMMO_SUCCESS;
}

CommoResult FTPCloudClient::createCollectionInit(int *cloudIOid,
                                                      const char *path)
{
    std::string rpath = basePath;
    rpath += path;
    removeExtraSlashes(&rpath);
    FTPCloudURLRequest *r = new FTPCloudURLRequest(CLOUDIO_OP_MAKE_COLLECTION,
                                 URLRequest::BUFFER_DOWNLOAD,
                                 baseUrl,
                                 "/",
                                 "",
                                 useLogin,
                                 user,
                                 pass,
                                 isSSL,
                                 caCerts);
    r->setDestPath(rpath);
    owner->urlManager->initRequest(cloudIOid, this, r);
    return COMMO_SUCCESS;
}


/***************************************************************************/
// FTPCloudURLRequest

FTPCloudURLRequest::FTPCloudURLRequest(CloudIOOperation op,
               URLRequestType type,
               const std::string &baseUrl,
               const std::string &requestPath,
               const char *localFileName,
               bool useLogin,
               const std::string &user,
               const std::string &pass,
               bool useSSL,
               STACK_OF(X509) *caCerts) :
        URLRequest(type, baseUrl + requestPath, 
                   localFileName, useSSL, caCerts),
        op(op),
        useLogin(useLogin),
        user(user),
        pass(pass),
        requestPath(requestPath),
        customHeaders(NULL),
        srcPath(),
        destPath()
{
}

FTPCloudURLRequest::~FTPCloudURLRequest()
{
    std::vector<InternalCloudCollectionEntry *>::iterator iter;
    for (iter = entries.begin(); iter != entries.end(); ++iter) {
        InternalCloudCollectionEntry *e = *iter;
        delete e;
    }
    if (customHeaders)
        curl_slist_free_all(customHeaders);
}

void FTPCloudURLRequest::curlExtraConfig(CURL *curlCtx)
                           COMMO_THROW (IOStatusException)
{
    if (useLogin) {
        CURL_CHECK(curl_easy_setopt(curlCtx, CURLOPT_USERNAME,
                                    user.c_str()));
        CURL_CHECK(curl_easy_setopt(curlCtx, CURLOPT_PASSWORD,
                                    pass.c_str()));
    }

    switch (op) {
    case CLOUDIO_OP_LIST_COLLECTION:
        break;
    case CLOUDIO_OP_MOVE:
        {
            std::string shdr = "RNFR ";
            shdr += srcPath;
            std::string dhdr = "RNTO ";
            dhdr += destPath;
            customHeaders = curl_slist_append(customHeaders, shdr.c_str());
            customHeaders = curl_slist_append(customHeaders, dhdr.c_str());
            CURL_CHECK(curl_easy_setopt(curlCtx, CURLOPT_QUOTE, customHeaders));
            break;
        }
    case CLOUDIO_OP_MAKE_COLLECTION:
        {
            std::string shdr = "MKD ";
            shdr += destPath;
            customHeaders = curl_slist_append(customHeaders, shdr.c_str());
            CURL_CHECK(curl_easy_setopt(curlCtx, CURLOPT_QUOTE, customHeaders));
            break;
        }
    case CLOUDIO_OP_TEST_SERVER:
    case CLOUDIO_OP_GET:
    case CLOUDIO_OP_PUT:
        break;
    }
}

URLIOUpdate *FTPCloudURLRequest::createUpdate(
                const int xferid,
                SimpleFileIOStatus status,
                const char *additionalInfo,
                uint64_t bytesTransferred,
                uint64_t totalBytesToTransfer)
{
    size_t n = 0;
    const CloudCollectionEntry **earr = NULL;
    if (op == CLOUDIO_OP_LIST_COLLECTION && status == FILEIO_SUCCESS) {
        // First, if we have leftover data buffered up, flush-parse it
        if (curEntryBuf.length() > 0) {
            parseEntry();
            curEntryBuf = "";
        }
        std::vector<InternalCloudCollectionEntry *>::iterator iter;
        n = entries.size();
        earr = new const CloudCollectionEntry *[n];
        size_t i = 0;
        for (iter = entries.begin(); iter != entries.end(); ++iter) {
            InternalCloudCollectionEntry *e = *iter;
            earr[i++] = e; 
        }
        entries.clear();
    }

    InternalCloudUpdate *ret = new InternalCloudUpdate(op, xferid,
                                   status, additionalInfo,
                                   bytesTransferred,
                                   totalBytesToTransfer,
                                   earr,
                                   n);
    return ret;
}

SimpleFileIOStatus FTPCloudURLRequest::statusForResponse(int response)
{
    SimpleFileIOStatus ret;
    switch (response) {
        case 401:
            ret = FILEIO_AUTH_ERROR;
            break;
        case 403:
            ret = FILEIO_ACCESS_DENIED;
            break;
        case 404:
        case 410:
            ret = FILEIO_URL_NO_RESOURCE;
            break;
        case 405:
            ret = FILEIO_URL_UNSUPPORTED;
            break;
        case 200:
        case 201:
        case 202:
        case 204:
        case 207:

        case 226:
            ret = FILEIO_SUCCESS;
            break;
        default:
            ret = FILEIO_OTHER_ERROR;
            break;
    }
    if (response > 200 && response < 300)
        ret = FILEIO_SUCCESS;
    else if (response > 400 && response < 600) {
        switch (response) {
        case 530:
            ret = FILEIO_AUTH_ERROR;
            break;
        case 550:
            ret = FILEIO_URL_NO_RESOURCE;
            break;
        default:
            ret = FILEIO_OTHER_ERROR;
            break;
        }
    } else
        ret = FILEIO_OTHER_ERROR;

    //printf("STATUS FROM CURL: %d\n", response);
    //fflush(stdout);
    return ret;
}


void FTPCloudURLRequest::downloadedData(uint8_t *data, size_t len)
{
    //std::string s((const char *)data, len);
    //printf("BUF: %s\n", s.c_str());
    //fflush(stdout);
    if (op != CLOUDIO_OP_LIST_COLLECTION)
        return;
    
    size_t start = 0;
    bool reset = false;
    for (size_t i = 0; i < len; ++i) {
        if (reset) {
            start = i;
            reset = false;
        }
        if (data[i] == '\r' || data[i] == '\n') {
            // Process the buffered line
            if (curEntryBuf.length() > 0 || i != start) {
                curEntryBuf += std::string((const char *)data + start, i - start);
                parseEntry();
                curEntryBuf = "";
            }
            reset = true;
        }
    }
    if (reset) {
        // This just covers the case where the end of incoming data was
        // CR or LF, in which case we don't carry over the remaining stuff
        start = len;
    }
    std::string leftovers((const char *)data + start, len - start);
    curEntryBuf += leftovers;
}

void FTPCloudURLRequest::parseEntry()
{
    struct ftpparse fp;
    memset(&fp, 0, sizeof(fp));
    const char *buf = curEntryBuf.c_str();
    if (ftpparse(&fp, buf, curEntryBuf.length()) == 1) {
        bool isDir = (fp.flagtrycwd == 1 && fp.flagtryretr == 0);
        uint64_t len = CloudCollectionEntry::FILE_SIZE_UNKNOWN;
        if (fp.sizetype != FTPPARSE_SIZE_UNKNOWN)
            len = fp.size;
        std::string name(fp.name, fp.namelen);
        pushEntry(name, isDir, len);
    }
    
}

void FTPCloudURLRequest::pushEntry(const std::string &name,
                                   bool isDir, uint64_t len)
{
    if (name == "." || name == "..")
        return;
    
    std::string path = requestPath;
    path += "/";
    path += name;
    removeExtraSlashes(&path);

    entries.push_back(new InternalCloudCollectionEntry(
                       isDir ? CloudCollectionEntry::Type::TYPE_COLLECTION :
                       CloudCollectionEntry::Type::TYPE_FILE,
                       path,
                       len));
}

void FTPCloudURLRequest::setDestPath(const std::string &path)
{
    destPath = path;
}

void FTPCloudURLRequest::setRenameSrcPath(const std::string &path)
{
    srcPath = path;
}



    

/***************************************************************************/
// OwncloudClient - internals


OwncloudClient::OwncloudClient(
                  CloudIOManager *owner,
                  CloudIO *clientIO,
                  CloudIOProtocol proto,
                  const char *host,
                  int port,
                  const char *basePath,
                  const char *user,
                  const char *pass,
                  const uint8_t *caCert,
                  const size_t caCertLen,
                  const char *caCertPassword) COMMO_THROW (CommoResult) :
        InternalCloudClient(owner, clientIO, proto, user, pass), 
        isSSL(false),
        basePath(""),
        capsPath(""),
        baseUrl("")
{
    switch (proto) {
    case CLOUDIO_PROTO_HTTPS:
        isSSL = true;
        baseUrl = "https://";
        break;
    case CLOUDIO_PROTO_HTTP:
        isSSL = false;
        baseUrl = "http://";
        break;
    default:
        throw COMMO_ILLEGAL_ARGUMENT;
    }
    std::string basePathTrimmed = basePath;
    // Always lead basePath with a / since client may not have included one
    // and if they did, our duplicate removal will take care of it
    basePathTrimmed = "/";
    basePathTrimmed += basePath;
    std::string capsPath = basePathTrimmed;
    basePathTrimmed += OWNCLOUD_WEBDAV_PATH;
    capsPath += OWNCLOUD_CAPS_PATH;
    removeExtraSlashes(&basePathTrimmed);
    removeExtraSlashes(&capsPath);

    this->basePath = basePathTrimmed;
    this->capsPath = capsPath;

    baseUrl += host;
    baseUrl += ":";
    baseUrl += InternalUtils::intToString(port);

    if (isSSL && caCert && caCertLen) {
        parseCerts(caCert, caCertLen,
                    caCertPassword);
    }
}

OwncloudClient::~OwncloudClient()
{
}

void OwncloudClient::urlRequestUpdate(URLIOUpdate *update)
{
    const CloudIOUpdate *cup = (const CloudIOUpdate *)update->getBaseUpdate();
    clientIO->cloudOperationUpdate(cup);
}



/***************************************************************************/
// OwncloudClient - public API

CommoResult OwncloudClient::testServerInit(int *cloudIOid)
{
    OwncloudURLRequest *r = new OwncloudURLRequest(CLOUDIO_OP_TEST_SERVER,
                                 URLRequest::BUFFER_DOWNLOAD,
                                 baseUrl,
                                 capsPath,
                                 "",
                                 "",
                                 useLogin,
                                 user,
                                 pass,
                                 isSSL,
                                 caCerts);
    owner->urlManager->initRequest(cloudIOid, this, r);
    return COMMO_SUCCESS;
}

CommoResult OwncloudClient::listCollectionInit(int *cloudIOid,
                                                    const char *path)
{
    std::string rpath = "/";
    rpath += path;
    // Ownclud will list directories with or without a trailing slash, but
    // always puts trailing slashes on directory resource listing returns
    // so make sure we send the request with it so that we can skip
    // the listing result for the request path itself regardless of if the
    // user request had the slash at the end.
    rpath += "/";
    removeExtraSlashes(&rpath);
    OwncloudURLRequest *r = new OwncloudURLRequest(CLOUDIO_OP_LIST_COLLECTION,
                                 URLRequest::BUFFER_DOWNLOAD,
                                 baseUrl,
                                 basePath,
                                 rpath,
                                 "",
                                 useLogin,
                                 user,
                                 pass,
                                 isSSL,
                                 caCerts);
    owner->urlManager->initRequest(cloudIOid, this, r);
    return COMMO_SUCCESS;
}

CommoResult OwncloudClient::getFileInit(int *cloudIOid,
                                             const char *localFile,
                                             const char *remotePath)
{
    std::string rpath = "/";
    rpath += remotePath;
    removeExtraSlashes(&rpath);

    OwncloudURLRequest *r = new OwncloudURLRequest(CLOUDIO_OP_GET,
                                 URLRequest::FILE_DOWNLOAD,
                                 baseUrl,
                                 basePath,
                                 rpath,
                                 localFile,
                                 useLogin,
                                 user,
                                 pass,
                                 isSSL,
                                 caCerts);
    owner->urlManager->initRequest(cloudIOid, this, r);
    return COMMO_SUCCESS;
}
                                    
CommoResult OwncloudClient::putFileInit(int *cloudIOid,
                                             const char *remotePath,
                                             const char *localFile)
{
    std::string rpath = "/";
    rpath += remotePath;
    removeExtraSlashes(&rpath);
    OwncloudURLRequest *r = new OwncloudURLRequest(CLOUDIO_OP_PUT,
                                 URLRequest::FILE_UPLOAD,
                                 baseUrl,
                                 basePath,
                                 rpath,
                                 localFile,
                                 useLogin,
                                 user,
                                 pass,
                                 isSSL,
                                 caCerts);
    owner->urlManager->initRequest(cloudIOid, this, r);
    return COMMO_SUCCESS;
}

CommoResult OwncloudClient::moveResourceInit(int *cloudIOid,
                                                  const char *fromPath,
                                                  const char *toPath)
{
    std::string tpath = "/";
    tpath += toPath;
    removeExtraSlashes(&tpath);
    std::string fpath = "/";
    fpath += fromPath;
    removeExtraSlashes(&fpath);

    OwncloudURLRequest *r = new OwncloudURLRequest(CLOUDIO_OP_MOVE,
                                 URLRequest::BUFFER_DOWNLOAD,
                                 baseUrl,
                                 basePath,
                                 fpath,
                                 "",
                                 useLogin,
                                 user,
                                 pass,
                                 isSSL,
                                 caCerts);
    r->setDestPath(tpath);
    owner->urlManager->initRequest(cloudIOid, this, r);
    return COMMO_SUCCESS;
}

CommoResult OwncloudClient::createCollectionInit(int *cloudIOid,
                                                      const char *path)
{
    std::string rpath = "/";
    rpath += path;
    removeExtraSlashes(&rpath);
    OwncloudURLRequest *r = new OwncloudURLRequest(CLOUDIO_OP_MAKE_COLLECTION,
                                 URLRequest::BUFFER_DOWNLOAD,
                                 baseUrl,
                                 basePath,
                                 rpath,
                                 "",
                                 useLogin,
                                 user,
                                 pass,
                                 isSSL,
                                 caCerts);
    owner->urlManager->initRequest(cloudIOid, this, r);
    return COMMO_SUCCESS;
}


    

/***************************************************************************/
// OwncloudURLRequest

OwncloudURLRequest::OwncloudURLRequest(CloudIOOperation op,
               URLRequestType type,
               const std::string &baseUrl,
               const std::string &servicePath,
               const std::string &requestPath,
               const char *localFileName,
               bool useLogin,
               const std::string &user,
               const std::string &pass,
               bool useSSL,
               STACK_OF(X509) *caCerts) :
        URLRequest(type, baseUrl + servicePath + requestPath, 
                   localFileName, useSSL, caCerts),
        op(op),
        useLogin(useLogin),
        user(user),
        pass(pass),
        servicePath(servicePath),
        requestPath(requestPath),
        customHeaders(NULL),
        saxHandler(),
        saxParseState(NOT_STARTED),
        xmlContext(NULL),
        entries(),
        curEntryPath(),
        curEntryType(CloudCollectionEntry::Type::TYPE_FILE),
        curEntryFileSize(),
        destPath()
{
    memset(&saxHandler, 0, sizeof(saxHandler));
    saxHandler.initialized = XML_SAX2_MAGIC;
    saxHandler.startDocument = startDocumentRedir;
    saxHandler.endDocument = endDocumentRedir;
    saxHandler.startElementNs = startElementRedir;
    saxHandler.endElementNs = endElementRedir;
    saxHandler.characters = charactersRedir;
    saxHandler.getEntity = getEntity;
}

OwncloudURLRequest::~OwncloudURLRequest()
{
    if (xmlContext) {
        xmlFreeParserCtxt(xmlContext);
        xmlContext = NULL;
    }

    std::vector<InternalCloudCollectionEntry *>::iterator iter;
    for (iter = entries.begin(); iter != entries.end(); ++iter) {
        InternalCloudCollectionEntry *e = *iter;
        delete e;
    }
    if (customHeaders)
        curl_slist_free_all(customHeaders);
}

void OwncloudURLRequest::curlExtraConfig(CURL *curlCtx)
                           COMMO_THROW (IOStatusException)
{
    if (useLogin) {
        CURL_CHECK(curl_easy_setopt(curlCtx, CURLOPT_USERNAME,
                                    user.c_str()));
        CURL_CHECK(curl_easy_setopt(curlCtx, CURLOPT_PASSWORD,
                                    pass.c_str()));
    }

    switch (op) {
    case CLOUDIO_OP_LIST_COLLECTION:
        CURL_CHECK(curl_easy_setopt(curlCtx, CURLOPT_CUSTOMREQUEST,
                                    "PROPFIND"));
        customHeaders = curl_slist_append(customHeaders, "Depth: 1");
        CURL_CHECK(curl_easy_setopt(curlCtx, CURLOPT_HTTPHEADER, customHeaders));
        break;
    case CLOUDIO_OP_MOVE:
        {
            std::string dhdr = "Destination:";
            dhdr += destPath;
            customHeaders = curl_slist_append(customHeaders, dhdr.c_str());
            CURL_CHECK(curl_easy_setopt(curlCtx, CURLOPT_CUSTOMREQUEST,
                                        "MOVE"));
            CURL_CHECK(curl_easy_setopt(curlCtx, CURLOPT_HTTPHEADER, customHeaders));
            break;
        }
    case CLOUDIO_OP_MAKE_COLLECTION:
        CURL_CHECK(curl_easy_setopt(curlCtx, CURLOPT_CUSTOMREQUEST,
                                    "MKCOL"));
        break;
    case CLOUDIO_OP_TEST_SERVER:
    case CLOUDIO_OP_GET:
    case CLOUDIO_OP_PUT:
        break;
    }
}

URLIOUpdate *OwncloudURLRequest::createUpdate(
                const int xferid,
                SimpleFileIOStatus status,
                const char *additionalInfo,
                uint64_t bytesTransferred,
                uint64_t totalBytesToTransfer)
{
    size_t n = 0;
    const CloudCollectionEntry **earr = NULL;
    if (xmlContext && status == FILEIO_SUCCESS) {
        // On success we need to flush the xml parser if it was in use
        // before looking at results
        xmlParseChunk(xmlContext, NULL, 0, 1);
        xmlFreeParserCtxt(xmlContext);
        xmlContext = NULL;
    }
    if (op == CLOUDIO_OP_LIST_COLLECTION && status == FILEIO_SUCCESS) {
        if (saxParseState == PARSE_ERROR) {
            status = FILEIO_OTHER_ERROR;
            additionalInfo = xmlError.c_str();
        } else {
            std::vector<InternalCloudCollectionEntry *>::iterator iter;
            n = entries.size();
            earr = new const CloudCollectionEntry *[n];
            size_t i = 0;
            for (iter = entries.begin(); iter != entries.end(); ++iter) {
                InternalCloudCollectionEntry *e = *iter;
                earr[i++] = e; 
            }
            entries.clear();
        }
    }
    if (op == CLOUDIO_OP_TEST_SERVER && status == FILEIO_SUCCESS) {
        if (saxParseState == PARSE_ERROR) {
            status = FILEIO_OTHER_ERROR;
            additionalInfo = xmlError.c_str();
        } else {
            additionalInfo = versionString.c_str();
        }
    }

    InternalCloudUpdate *ret = new InternalCloudUpdate(op, xferid,
                                   status, additionalInfo,
                                   bytesTransferred,
                                   totalBytesToTransfer,
                                   earr,
                                   n);
    return ret;
}

SimpleFileIOStatus OwncloudURLRequest::statusForResponse(int response)
{
    SimpleFileIOStatus ret;
    switch (response) {
        case 401:
            ret = FILEIO_AUTH_ERROR;
            break;
        case 403:
            ret = FILEIO_ACCESS_DENIED;
            break;
        case 404:
        case 410:
            ret = FILEIO_URL_NO_RESOURCE;
            break;
        case 405:
            ret = FILEIO_URL_UNSUPPORTED;
            break;
        case 200:
        case 201:
        case 202:
        case 204:
        case 207:
            ret = FILEIO_SUCCESS;
            break;
        default:
            ret = FILEIO_OTHER_ERROR;
            break;
    }
    return ret;
}


void OwncloudURLRequest::downloadedData(uint8_t *data, size_t len)
{
    //std::string s((const char *)data, len);
    //printf("BUF: %s\n", s.c_str());
    if ((op != CLOUDIO_OP_LIST_COLLECTION && op != CLOUDIO_OP_TEST_SERVER)
              || saxParseState == PARSE_ERROR)
        return;
    
    if (!xmlContext) {
        xmlContext = xmlCreatePushParserCtxt(&saxHandler, this,
                                (const char *)data,
                                (int)len, NULL);
        if (!xmlContext) {
            xmlError = "Failed to create xml parser";
            saxParseState = PARSE_ERROR;
        }
    } else {
        if (xmlParseChunk(xmlContext, (const char *)data, (int)len, 0) != 0) {
            xmlError = "XML parsing error";
            saxParseState = PARSE_ERROR;
        }
    }
}

void OwncloudURLRequest::pushEntry()
{
    if (curEntryPath.find(servicePath) == 0)
        curEntryPath = curEntryPath.substr(servicePath.length());

    // skip the request path itself
    if (curEntryPath.compare(requestPath) != 0) {
        uint64_t fs = CloudCollectionEntry::FILE_SIZE_UNKNOWN;
        if (!curEntryFileSize.empty()) {
            try {
                fs = InternalUtils::uint64FromString(curEntryFileSize.c_str());
            } catch (std::invalid_argument &) {
            }
        }

        entries.push_back(new InternalCloudCollectionEntry(curEntryType,
                                                           curEntryPath,
                                                           fs));
    }

    curEntryPath = "";
    curEntryType = CloudCollectionEntry::Type::TYPE_FILE;
    curEntryFileSize = "";
}

void OwncloudURLRequest::setDestPath(const std::string &path)
{
    destPath = path;
}

void OwncloudURLRequest::startDocumentRedir(void *ctx)
{
    OwncloudURLRequest *p = (OwncloudURLRequest *)ctx;
    if (p->saxParseState == PARSE_ERROR)
        return;

    if (p->saxParseState != NOT_STARTED) {
        p->saxParseState = PARSE_ERROR;
        p->xmlError = "Start of document encountered without ending previous document";
    }
    p->saxParseState = IN_DOC;
}

void OwncloudURLRequest::endDocumentRedir(void *ctx)
{
    OwncloudURLRequest *p = (OwncloudURLRequest *)ctx;
    if (p->saxParseState == PARSE_ERROR)
        return;

    p->saxParseState = COMPLETE;
}

void OwncloudURLRequest::startElementRedir(void *ctx, const xmlChar *name,
                                        const xmlChar *prefix,
                                        const xmlChar *namespaceUri,
                                        int numNamespaces,
                                        const xmlChar **namespaces,
                                        int numAttribs,
                                        int numDefaulted,
                                        const xmlChar **attribs)
{
    OwncloudURLRequest *p = (OwncloudURLRequest *)ctx;
    if (p->saxParseState == PARSE_ERROR)
        return;
    bool isDAV = namespaceUri && xmlStrcmp(namespaceUri, (xmlChar *)"DAV:") == 0;
    bool isTest = p->op == CLOUDIO_OP_TEST_SERVER;

    switch (p->saxParseState) {
      case NOT_STARTED:
      case COMPLETE:
        p->saxParseState = PARSE_ERROR;
        p->xmlError = "Element outside doc start";
        break;
      case IN_DOC:
        if (isTest) {
            if (xmlStrcmp(name, (xmlChar *)"version") == 0)
                p->saxParseState = TEST_IN_VERSION;
            else if (xmlStrcmp(name, (xmlChar *)"webdav-root") == 0)
                p->saxParseState = TEST_IN_DAVROOT;
        } else {
            if (isDAV && xmlStrcmp(name, (xmlChar *)"response") == 0)
                p->saxParseState = IN_RESP;
        }
        break;
      case TEST_IN_VERSION:
        if (xmlStrcmp(name, (xmlChar *)"string") == 0)
            p->saxParseState = TEST_IN_VSTRING;
        break;      
      case IN_RESP:
        if (isDAV && xmlStrcmp(name, (xmlChar *)"href") == 0)
            p->saxParseState = IN_REF;
        else if (isDAV && xmlStrcmp(name, (xmlChar *)"resourcetype") == 0)
            p->saxParseState = IN_RESOURCETYPE;
        else if (isDAV && xmlStrcmp(name, (xmlChar *)"getcontentlength") == 0)
            p->saxParseState = IN_CONTENT_LEN;
        break;
      case IN_CONTENT_LEN:
      case IN_REF:
      case TEST_IN_VSTRING:
      case TEST_IN_DAVROOT:
        p->saxParseState = PARSE_ERROR;
        p->xmlError = "Unexpected element inside text element";
        break;
      case IN_RESOURCETYPE:
        if (isDAV && xmlStrcmp(name, (xmlChar *)"collection") == 0)
            p->curEntryType = CloudCollectionEntry::Type::TYPE_COLLECTION;
        break;
      case PARSE_ERROR:
        break;
    }

}

void OwncloudURLRequest::endElementRedir(void *ctx, const xmlChar *name,
                                      const xmlChar *prefix,
                                      const xmlChar *namespaceUri)
{
    OwncloudURLRequest *p = (OwncloudURLRequest *)ctx;
    if (p->saxParseState == PARSE_ERROR)
        return;
    bool isDAV = namespaceUri && xmlStrcmp(namespaceUri, (xmlChar *)"DAV:") == 0;

    switch (p->saxParseState) {
      case NOT_STARTED:
      case COMPLETE:
        p->saxParseState = PARSE_ERROR;
        p->xmlError = "Element outside doc start";
        break;
      case IN_DOC:
        break;
      case TEST_IN_VERSION:
        if (xmlStrcmp(name, (xmlChar *)"version") == 0)
            p->saxParseState = IN_DOC;
        break;
      case TEST_IN_VSTRING:
        if (xmlStrcmp(name, (xmlChar *)"string") == 0)
            p->saxParseState = TEST_IN_VERSION;
        break;
      case TEST_IN_DAVROOT:
        if (xmlStrcmp(name, (xmlChar *)"webdav-root") == 0)
            p->saxParseState = IN_DOC;
        break;
      
      case IN_RESP:
        if (isDAV && xmlStrcmp(name, (xmlChar *)"response") == 0) {
            p->saxParseState = IN_DOC;
            p->pushEntry();
        }
        break;
      case IN_REF:
        if (isDAV && xmlStrcmp(name, (xmlChar *)"href") == 0)
            p->saxParseState = IN_RESP;
        break;
      case IN_RESOURCETYPE:
        if (isDAV && xmlStrcmp(name, (xmlChar *)"resourcetype") == 0)
            p->saxParseState = IN_RESP;
        break;
      case IN_CONTENT_LEN:
        if (isDAV && xmlStrcmp(name, (xmlChar *)"getcontentlength") == 0)
            p->saxParseState = IN_RESP;
        break;
      case PARSE_ERROR:
        break;
    }

}

void OwncloudURLRequest::charactersRedir(void *ctx, const xmlChar *text, int len)
{
    OwncloudURLRequest *p = (OwncloudURLRequest *)ctx;
    if (p->saxParseState == IN_REF) {
        std::string s((const char *)text, len);
        p->curEntryPath += s;
    }
    else if (p->saxParseState == IN_CONTENT_LEN) {
        std::string s((const char *)text, len);
        p->curEntryFileSize += s;
    }
    else if (p->saxParseState == TEST_IN_VSTRING) {
        std::string s((const char *)text, len);
        p->versionString += s;
    }
    else if (p->saxParseState == TEST_IN_DAVROOT) {
        std::string s((const char *)text, len);
        p->testDavRoot += s;
    }
}

xmlEntityPtr OwncloudURLRequest::getEntity(void *ctx, const xmlChar *name)
{
    return xmlGetPredefinedEntity(name);
}







/***************************************************************************/
// Internal impl objects

InternalCloudUpdate::InternalCloudUpdate(CloudIOOperation op,
                                const int xferid,
                                const SimpleFileIOStatus status,
                                const char *additionalInfo,
                                uint64_t bytesTransferred,
                                uint64_t totalBytesToTransfer,
                                const CloudCollectionEntry **entries,
                                size_t numEntries) : 
        URLIOUpdate(), 
        CloudIOUpdate(op, xferid, status,
                      additionalInfo ? new char[strlen(additionalInfo) + 1] :
                                       NULL,
                      bytesTransferred, totalBytesToTransfer,
                      entries, numEntries)
{
    if (additionalInfo)
        strcpy(const_cast<char * const>(this->additionalInfo), additionalInfo);
}

InternalCloudUpdate::~InternalCloudUpdate()
{
    if (entries) {
        for (size_t i = 0; i < numEntries; ++i)
            delete (const InternalCloudCollectionEntry *)(entries[i]);
        delete[] entries;
    }

    if (additionalInfo)
        delete[] additionalInfo;
}

SimpleFileIOUpdate *InternalCloudUpdate::getBaseUpdate()
{
    return this;
}


InternalCloudCollectionEntry::InternalCloudCollectionEntry(Type type, 
                                           const std::string &path,
                                           uint64_t fileSize) : 
        CloudCollectionEntry(type, new char[path.length() + 1], fileSize)
{
    strcpy(const_cast<char * const>(this->path), path.c_str());
}

InternalCloudCollectionEntry::~InternalCloudCollectionEntry()
{
    delete[] path;
}


