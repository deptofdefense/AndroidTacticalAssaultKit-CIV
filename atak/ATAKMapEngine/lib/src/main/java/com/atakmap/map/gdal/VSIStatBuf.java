package com.atakmap.map.gdal;

/**
 * VSIStatBuf is a java representation of the stat struct (man 2 stat).
 */
public class VSIStatBuf {
    /* ID of device containing file */
    public long st_dev;
    /* Inode number */
    public long st_ino;
    /* File type and mode */
    public int st_mode;
    /* Number of hard links */
    public long st_nlink;
    /* User ID of owner */
    public int st_uid;
    /* Group ID of owner */
    public int st_gid;
    /* Device ID (if special file) */
    public long st_rdev;
    /* Total size, in bytes */
    public long st_size;
    /* Block size for filesystem I/O */
    public long st_blksize;
    /* Number of 512B blocks allocated */
    public long st_blocks;
    /* Time of last access */
    public long st_atime;
    /* Time of last modification */
    public long st_mtime;
    /* Time of last status change */
    public long st_ctime;
}
