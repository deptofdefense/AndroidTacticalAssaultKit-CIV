
#include <stdio.h>
#include <string.h>
#include <sqlite3.h>
#include "auth-db.h"

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

void logBytes(const char* file, int line, const char* val) 
{
	char buffer[BUFFER_SIZE*5];
	memset((void*)buffer, 0, BUFFER_SIZE*5);

	int i;
	for (i = 0; i < strlen(val); i++)
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
		"create table if not exists credentials(type TEXT, site TEXT, username TEXT, password TEXT, expires INTEGER);", 
		0, 0, 0);		

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

int getDistinctSitesAndTypes(char*** sites, char*** types, int* size)
{
	sqlite3_stmt* stmt = 0;
	*sites = 0;
	*types = 0;
	*size = 0;
	
	//
	// count the entries
	//

	int rc = sqlite3_prepare(g_db, 
		"select count(*) from (select distinct site, type from credentials); ",
		-1, &stmt, 0);
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
		logString(__FILE__, __LINE__, "no credentials found");
		return SQLITE_OK;
	}
	
	logInt(__FILE__, __LINE__, *size);

	//
	// allocate the arrays
	//

	*sites = (char**) calloc(*size, sizeof(char**));	
	*types = (char**) calloc(*size, sizeof(char**));	
	
	//
	// populate the arrays
	//
	
	rc = sqlite3_prepare(g_db, 
		"select distinct site, type from credentials;", 
		-1, &stmt, 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}

	int index = 0;
	while (sqlite3_step(stmt) == SQLITE_ROW)
	{
		char* siteTemp = (char*)sqlite3_column_text(stmt, 0);
		char* typeTemp = (char*)sqlite3_column_text(stmt, 1);
		
		if (siteTemp != 0)
		{
			char* siteCopy = malloc(strlen(siteTemp) + 1); 
			strcpy(siteCopy, siteTemp);
			(*sites)[index] = siteCopy;
		}
		
		if (typeTemp != 0)
		{
			char* typeCopy = malloc(strlen(typeTemp) + 1); 
			strcpy(typeCopy, typeTemp);
			(*types)[index] = typeCopy;
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

int getCredentials(const char* type, const char* site, char** username, char** password)
{
	sqlite3_stmt* stmt = 0;
	*username = 0;
	*password = 0;
		
	int rc = sqlite3_prepare(g_db, 
		"select username, password from credentials where type = ? and site = ?;", 
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
	
	rc = sqlite3_bind_text(stmt, 2, site, strlen(site), 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}

	rc = sqlite3_step(stmt);
	if(rc == SQLITE_ROW)
	{
		char* usernameTmp = (char*)sqlite3_column_text(stmt, 0);
		if (usernameTmp != 0)
		{
			char* usernameCopy = malloc(strlen(usernameTmp) + 1); 
			strcpy(usernameCopy, usernameTmp);
			*username = usernameCopy;
		}
		
		char* passwordTmp = (char*)sqlite3_column_text(stmt, 1);
		if (passwordTmp != 0)
		{
			char* passwordCopy = malloc(strlen(passwordTmp) + 1); 
			strcpy(passwordCopy, passwordTmp);
			*password = passwordCopy;
		}
	}
	else
	{
		logInt(__FILE__, __LINE__, rc);
		g_log("no credentials found.");
	}

	rc = sqlite3_finalize(stmt);
	if(rc != SQLITE_OK)
	{
		logInt(__FILE__, __LINE__, rc);
	}
	
	return rc;
}

int saveCredentials(
	const char* type, const char* site, 
	const char* username, const char* password, 
	const long long expires) 
{
	sqlite3_stmt* stmt = 0;
	char* usernameTmp = 0;
	char* passwordTmp = 0;
	int rc = 0;

	rc = getCredentials(type, site, &usernameTmp, &passwordTmp);
	if (rc != SQLITE_OK || passwordTmp == 0) 
	{
		g_log("no existing credentials");
		
		rc = sqlite3_prepare(g_db, 
			"insert into credentials ( type, site, username, password, expires ) values ( ?, ?, ?, ?, ? );", 
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
		
		rc = sqlite3_bind_text(stmt, 2, site, strlen(site), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}

		rc = sqlite3_bind_text(stmt, 3, username, strlen(username), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_text(stmt, 4, password, strlen(password), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_int64(stmt, 5, expires);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
	}
	else 
	{
		if (usernameTmp != 0) 
		{
			free(usernameTmp);
		}
		
		if (passwordTmp != 0)
		{
			free(passwordTmp);
		}
		
		g_log("found existing credentials");
		
		rc = sqlite3_prepare(g_db, 
			"update credentials set username = ?, password = ?, expires = ? where type = ? and site =  ?;", 
			-1, &stmt, 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}

		rc = sqlite3_bind_text(stmt, 1, username, strlen(username), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_text(stmt, 2, password, strlen(password), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_int64(stmt, 3, expires);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_text(stmt, 4, type, strlen(type), 0);
		if(rc != SQLITE_OK)
		{
			sqlite3_finalize(stmt);
			logInt(__FILE__, __LINE__, rc);
			return rc;
		}
		
		rc = sqlite3_bind_text(stmt, 5, site, strlen(site), 0);
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

int invalidate(const char* type, const char* site)
{
	sqlite3_stmt* stmt = 0;
	
	int rc = sqlite3_prepare(g_db, 
		"delete from credentials where type = ? and site = ?;", 
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
	
	rc = sqlite3_bind_text(stmt, 2, site, strlen(site), 0);
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

int deleteExpiredCredentials(long long time) 
{
	sqlite3_stmt* stmt = 0;
	
	int rc = sqlite3_prepare(g_db, 
		"delete from credentials where expires > 0 and expires <= ?;", 
		-1, &stmt, 0);
	if(rc != SQLITE_OK)
	{
		sqlite3_finalize(stmt);
		logInt(__FILE__, __LINE__, rc);
		return rc;
	}
	
	rc = sqlite3_bind_int64(stmt, 1, time);
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








	