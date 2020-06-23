
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <sqlite3.h>
#include "cert-db.h"

static sqlite3* g_db = 0;
static pfnLogger g_log = 0;

const int BUFFER_SIZE = 255;

void setLogger(pfnLogger logger) 
{
	g_log = logger;
}

void logInt(const char* file, int line, int val)
{
	char buffer[BUFFER_SIZE];
	memset((void*)buffer, 0, BUFFER_SIZE);
	sprintf(buffer, "%s,%d: val = %d", file, line, val);
	g_log(buffer);
}

void logStr(const char* file, int line, const char* val)
{
	char buffer[BUFFER_SIZE];
	memset((void*)buffer, 0, BUFFER_SIZE);
	sprintf(buffer, "%s,%d: val = %s", file, line, val);
	g_log(buffer);
}

void logBytes(const char* file, int line, const char* val) 
{
	char buffer[BUFFER_SIZE*5];
	memset((void*)buffer, 0, BUFFER_SIZE*5);

	for (int i = 0; i < strlen(val); i++)
	{
		sprintf(buffer + (i*2), "%02X", val[i]); 
	}
	
	g_log("obfuscated pwd: ");
	g_log(buffer);
}

int openOrCreateDatabase(const char* filename, const char* password) 
{
	int rc = sqlite3_open(filename, &g_db);
	if(rc != SQLITE_OK)
	{
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}
	
	int length = strlen(password);
	
	// key the database with the password
	rc = sqlite3_key(g_db, password, length);

	if(rc != SQLITE_OK)
	{
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}
	
	rc = sqlite3_exec(g_db, 
	"create table if not exists certificates(type TEXT, certificate BLOB, hash TEXT);", 
		0, 0, 0);
	
	rc = sqlite3_exec(g_db, 
		"create table if not exists server_certificates (type TEXT, server TEXT, certificate BLOB, hash TEXT);", 
		0, 0, 0);
	if(rc != SQLITE_OK)
	{
		logInt(__FILE__, __LINE__, rc);
	}

	return rc;
}

int closedb(void)
{
	int rc = sqlite3_close(g_db);
	if (rc != SQLITE_OK)
	{
		logInt(__FILE__, __LINE__, rc);
	}
	
	return rc;
}

int getCertificate(const char* type, void** certificate, int* size, char** hash)
{
	sqlite3_stmt* stmt = 0;
	*certificate = 0;
	*size = 0;
	*hash = 0;
	
	int rc = sqlite3_prepare(g_db, 
		"select certificate, hash from certificates where type = ?;", 
		-1, &stmt, 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}
	
	rc = sqlite3_bind_text(stmt, 1, type, strlen(type), 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}
	
	rc = sqlite3_step(stmt);
	if(rc == SQLITE_ROW)
	{
		void* certificateTemp = (void*)sqlite3_column_blob(stmt, 0);
		*size = sqlite3_column_bytes(stmt, 0);
		
		char* certCopy = malloc(*size);
		memcpy((void*)certCopy, certificateTemp, *size);
		*certificate = certCopy;
		
		char* hashTmp = (char*)sqlite3_column_text(stmt, 1);
		if (hashTmp != 0)
		{
			char* hashCopy = malloc(strlen(hashTmp) + 1); 
			strcpy(hashCopy, hashTmp);
			*hash = hashCopy;
		}
	}

	rc = sqlite3_finalize(stmt);
	if(rc != SQLITE_OK)
	{
		logInt(__FILE__, __LINE__, rc);
	}
	
	return rc;
}

int saveCertificate(const char* type, const void* certificate, const int size, const char* hash)
{
	sqlite3_stmt* stmt = 0;
	void* certTmp = 0;
	int sizeTmp = 0;
	char* hashTmp = 0;
	int rc = 0;

	logInt(__FILE__, __LINE__, size);
	
	rc = getCertificate(type, &certTmp, &sizeTmp, &hashTmp);
	if (rc != SQLITE_OK || sizeTmp == 0) 
	{
		rc = sqlite3_prepare(g_db, 
			"insert into certificates ( type, certificate, hash ) values ( ?, ?, ? );", 
			-1, &stmt, 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_text(stmt, 1, type, strlen(type), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_blob(stmt, 2, certificate, size, 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_text(stmt, 3, hash, strlen(hash), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
	}
	else 
	{
		free(certTmp);
		if (hashTmp != 0)
		{
			free(hashTmp);
		}		
		
		rc = sqlite3_prepare(g_db, 
			"update certificates set certificate = ?, hash = ? where type = ?;", 
			-1, &stmt, 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_blob(stmt, 1, certificate, size, 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}

		rc = sqlite3_bind_text(stmt, 2, hash, strlen(hash), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_text(stmt, 3, type, strlen(type), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
	}
	
	rc = sqlite3_step(stmt);
	if(rc != SQLITE_DONE)
	{
		logInt(__FILE__, __LINE__, rc);
	}
	
	rc = sqlite3_finalize(stmt);
	if(rc != SQLITE_OK)
	{
		logInt(__FILE__, __LINE__, rc);
	}
	
	return rc;
}

int getCertificateForServer(const char* type, const char* server, void** certificate, int* size, char** hash)
{
	sqlite3_stmt* stmt = 0;
	*certificate = 0;
	*size = 0;
	*hash = 0;
	
	int rc = sqlite3_prepare(g_db, 
		"select certificate, hash from server_certificates where type = ? and server = ?;", 
		-1, &stmt, 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}
	
	rc = sqlite3_bind_text(stmt, 1, type, strlen(type), 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}

	rc = sqlite3_bind_text(stmt, 2, server, strlen(server), 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}
	
	rc = sqlite3_step(stmt);
	if(rc == SQLITE_ROW)
	{
		void* certificateTemp = (void*)sqlite3_column_blob(stmt, 0);
		*size = sqlite3_column_bytes(stmt, 0);
		
		char* certCopy = malloc(*size);
		memcpy((void*)certCopy, certificateTemp, *size);
		*certificate = certCopy;
		
		char* hashTmp = (char*)sqlite3_column_text(stmt, 1);
		if (hashTmp != 0)
		{
			char* hashCopy = malloc(strlen(hashTmp) + 1); 
			strcpy(hashCopy, hashTmp);
			*hash = hashCopy;
		}
	}

	rc = sqlite3_finalize(stmt);
	if(rc != SQLITE_OK)
	{
		logInt(__FILE__, __LINE__, rc);
	}
	
	return rc;
}

int getServers(const char* type, char*** servers, int* size)
{
	sqlite3_stmt* stmt = 0;
	*servers = 0;
	*size = 0;
	
	//
	// count the entries
	//

	int rc = sqlite3_prepare(g_db, 
		"select count(*) from server_certificates where type = ? and (server is not null and length(server) != 0);", 
		-1, &stmt, 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}
	
	rc = sqlite3_bind_text(stmt, 1, type, strlen(type), 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}

	rc = sqlite3_step(stmt);
	if (rc == SQLITE_ROW)
	{
		*size = sqlite3_column_int(stmt, 0);
	}

	rc = sqlite3_finalize(stmt);
	if(rc != SQLITE_OK)
	{
		logInt(__FILE__, __LINE__, rc);
	}

	if (*size == 0)
	{
		return SQLITE_OK;
	}
	
	logInt(__FILE__, __LINE__, *size);
	

	//
	// allocate the servers array
	//

	*servers = (char**) calloc(*size, sizeof(char**));	
	
	//
	// populate the servers
	//
	
	rc = sqlite3_prepare(g_db, 
		"select server from server_certificates where type = ? and (server is not null and length(server) != 0);", 
		-1, &stmt, 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}
	
	rc = sqlite3_bind_text(stmt, 1, type, strlen(type), 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}

	int index = 0;
	while (sqlite3_step(stmt) == SQLITE_ROW)
	{
		char* serverTemp = (char*)sqlite3_column_text(stmt, 0);
		if (serverTemp != 0)
		{
			char* serverCopy = malloc(strlen(serverTemp) + 1); 
			strcpy(serverCopy, serverTemp);
			(*servers)[index] = serverCopy;
		}
		
		index++;
	}

	rc = sqlite3_finalize(stmt);
	if(rc != SQLITE_OK)
	{
		logInt(__FILE__, __LINE__, rc);
	}
		
	return rc;
}

int saveCertificateForServer(const char* type, const char* server, const void* certificate, const int size, const char* hash)
{
	sqlite3_stmt* stmt = 0;
	void* certTmp = 0;
	int sizeTmp = 0;
	char* hashTmp = 0;
	int rc = 0;

	logInt(__FILE__, __LINE__, size);
	
	rc = getCertificateForServer(type, server, &certTmp, &sizeTmp, &hashTmp);
	if (rc != SQLITE_OK || sizeTmp == 0) 
	{
		rc = sqlite3_prepare(g_db, 
			"insert into server_certificates ( type, certificate, hash, server ) values ( ?, ?, ?, ? );", 
			-1, &stmt, 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_text(stmt, 1, type, strlen(type), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_blob(stmt, 2, certificate, size, 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_text(stmt, 3, hash, strlen(hash), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_text(stmt, 4, server, strlen(server), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}		
	}
	else 
	{
		free(certTmp);
		if (hashTmp != 0)
		{
			free(hashTmp);
		}		
		
		rc = sqlite3_prepare(g_db, 
			"update server_certificates set certificate = ?, hash = ? where type = ? and server = ?;", 
			-1, &stmt, 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_blob(stmt, 1, certificate, size, 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}

		rc = sqlite3_bind_text(stmt, 2, hash, strlen(hash), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_text(stmt, 3, type, strlen(type), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_text(stmt, 4, server, strlen(server), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
	}
	
	rc = sqlite3_step(stmt);
	if(rc != SQLITE_DONE)
	{
		logInt(__FILE__, __LINE__, rc);
	}
	
	rc = sqlite3_finalize(stmt);
	if(rc != SQLITE_OK)
	{
		logInt(__FILE__, __LINE__, rc);
	}
	
	return rc;
}

int deleteCertificate(const char* type)
{
	sqlite3_stmt* stmt = 0;
	
	int rc = sqlite3_prepare(g_db, 
		"delete from certificates where type = ?;", 
		-1, &stmt, 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}
	
	rc = sqlite3_bind_text(stmt, 1, type, strlen(type), 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}
	
	rc = sqlite3_step(stmt);
	if(rc != SQLITE_DONE)
	{
		logInt(__FILE__, __LINE__, rc);
	}

	rc = sqlite3_finalize(stmt);
	if(rc != SQLITE_OK)
	{
		logInt(__FILE__, __LINE__, rc);
	}
	
	return rc;
}

int deleteCertificateForServer(const char* type, const char* server)
{
	sqlite3_stmt* stmt = 0;
	
	int rc = sqlite3_prepare(g_db, 
		"delete from server_certificates where type = ? and server = ?;", 
		-1, &stmt, 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}
	
	rc = sqlite3_bind_text(stmt, 1, type, strlen(type), 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}
	
	rc = sqlite3_bind_text(stmt, 2, server, strlen(server), 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}	
	
	rc = sqlite3_step(stmt);
	if(rc != SQLITE_DONE)
	{
		logInt(__FILE__, __LINE__, rc);
	}

	rc = sqlite3_finalize(stmt);
	if(rc != SQLITE_OK)
	{
		logInt(__FILE__, __LINE__, rc);
	}
	
	return rc;
}







	