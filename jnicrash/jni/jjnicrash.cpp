#include "jjnicrash.h"

//#if defined(__arm__) || defined(__aarch64__)

#include <sys/types.h>
#include <string.h>
#include <string>
#include <signal.h>
#include <unistd.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdlib.h>
#include <time.h>
#include <dlfcn.h>
#include <cxxabi.h>

#if 0
#define UNW_LOCAL_ONLY
#include <libunwind.h>
#else
#include <unwindstack/Elf.h>
#include <unwindstack/MapInfo.h>
#include <unwindstack/Maps.h>
#include <unwindstack/Memory.h>
#include <unwindstack/Regs.h>
#endif


namespace {
    std::terminate_handler old_cxx_handler = NULL;
    bool handling_cxx_term = false;
    bool cxx_ex_handled = false;

    char *reportPreface = NULL;
    char *crash_filename = NULL;
    bool oldhandler_segf_set = false;
    struct sigaction oldhandler_segf;
    bool oldhandler_abrt_set = false;
    struct sigaction oldhandler_abrt;
    bool sighandler_in_progress = false;

// Big enough for a uint64
#define MAX_INT_CHARS sizeof(uint64_t) * CHAR_BIT + 2
    char printbuf[MAX_INT_CHARS];




    void call_old_or_exit(struct sigaction oldhandler, siginfo_t *si, void *arg) {
        if (oldhandler.sa_flags & SA_SIGINFO) {
            oldhandler.sa_sigaction(SIGSEGV, si, arg);
        } else if (oldhandler.sa_handler == SIG_DFL) {
            raise(SIGSEGV); // raise to trigger the default handler. It cannot be
                       // called directly.
        } else if (oldhandler.sa_handler != SIG_IGN) {
            // This handler can only handle to signal number (ANSI C)
            void (*previous_handler)(int) = oldhandler.sa_handler;
            previous_handler(SIGSEGV);
        }
        exit(1);
    }

    void write_string(int fd, const char *s)
    {
        write(fd, s, strlen(s));
    }

    void write_uint64(int fd, uint64_t v, unsigned int base)
    {
        // check that the base is valid
        if (base < 2 || base > 36)
            return;

        char* ptr = printbuf, *ptr1 = printbuf, tmp_char;
        uint64_t tmp_value;

        do {
            tmp_value = v;
            v /= base;
            *ptr++ = "zyxwvutsrqponmlkjihgfedcba9876543210123456789abcdefghijklmnopqrstuvwxyz" [35 + (tmp_value - v * base)];
        } while ( v );

        // Apply negative sign
        *ptr-- = '\0';
        while(ptr1 < ptr) {
            tmp_char = *ptr;
            *ptr--= *ptr1;
            *ptr1++ = tmp_char;
        }
        
        // If hex, do 0x for aesthetic
        if (base == 16)
            write(fd, "0x", 2);
        write(fd, printbuf, strlen(printbuf));
    }

    void write_preface(int fd)
    {
        write_string(fd, "Native crash report\nTimestamp = ");
        write_uint64(fd, time(NULL), 10);
        write_string(fd, "\n\n");
        if (reportPreface)
            write_string(fd, reportPreface);
        write_string(fd, "\n\n");
    }

    void close_crash_file(int fd)
    {
        close(fd);
    }

    int open_crash_file()
    {
        if (!crash_filename)
            return -1;

        int fd = open(crash_filename, O_CREAT | O_WRONLY, S_IRUSR | S_IWUSR);

        // -1 on error
        return fd;
    }
    
    void write_frame(int fd, unsigned frame, uint64_t ip, uint64_t sp)
    {
        write_string(fd, "#");
        write_uint64(fd, frame, 10);
        write_string(fd, " - ");
        write_uint64(fd, ip, 16);
        write_string(fd, "\n  stack pointer: ");
        write_uint64(fd, sp, 16);
        write_string(fd, "\n  ");

        Dl_info inf;
        uint64_t ipv = (uint64_t)ip;
        if (dladdr((void *)ip, &inf) == 0 || !inf.dli_sname) {
            write_string(fd, "(no known symbol - dynamic code)\n");
        } else {
            if (inf.dli_fname) {
                write_string(fd, inf.dli_fname);
            } else {
                write_string(fd, "(library unknown)");
            }
            write_string(fd, " ");
            write_string(fd, inf.dli_sname);
            write_string(fd, "\n    offset = ");
            write_uint64(fd, (ipv - (uint64_t)inf.dli_fbase), 16);
            write_string(fd, "\n");
            //fprintf(f, "  offset = %" PRIx64 " %" PRIx64 " %" PRIx64"\n", (ipv - (uint64_t)inf.dli_saddr), (ipv - (uint64_t)inf.dli_fbase), ((uint64_t)inf.dli_saddr - (uint64_t)inf.dli_fbase));
        }
    }
    
    void dump_stack(int fd, void *sigarg)
    {
        unwindstack::Regs *r;
        
        if (sigarg) {
            r = unwindstack::Regs::CreateFromUcontext(
                    unwindstack::Regs::CurrentArch(),
                    sigarg);
        } else {
            r = unwindstack::Regs::CreateFromLocal();
        }
        const std::unique_ptr<unwindstack::Regs> regs(r);

        std::string unw_function_name;
        unwindstack::LocalMaps maps;
        uint64_t ip, sp = 0;
        if (!maps.Parse()) {
            ip =  regs->pc();
            sp = regs->sp();
            write_string(fd, "Could not backtrace!\nGiving best info on crash point\n\n");
            write_frame(fd, 0, ip, sp);
        } else {
            const std::shared_ptr<unwindstack::Memory> memory(
                    new unwindstack::MemoryLocal);

            int frame_count = 0;
            while (true) {
                ip = regs->pc();
                sp = regs->sp();
                write_frame(fd, frame_count, ip, sp);

                frame_count++;

                unwindstack::MapInfo *const map_info = maps.Find(regs->pc());
                if (!map_info)
                    break;
                unwindstack::Elf *const elf = map_info->GetElf(memory, false);
                if (!elf) {
                    break;
                }

                // Getting value of program counter relative module where a function is
                // located.
                const uint64_t rel_pc = elf->GetRelPc(regs->pc(), map_info);
                uint64_t adjusted_rel_pc = rel_pc;
                if (frame_count != 0) {
                  // If it's not a first frame we need to rewind program counter value to
                  // previous instruction. For the first frame pc from ucontext points
                  // exactly to a failed instruction, for other frames rel_pc will contain
                  // return address after function call instruction.
                  adjusted_rel_pc -= regs->GetPcAdjustment(rel_pc, elf);
                }
                
                bool finished = false;
                if (!elf->Step(rel_pc, adjusted_rel_pc, map_info->elf_offset, regs.get(),
                               memory.get(), &finished) || finished) {
                  break;
                }
            }    
        }
    }
    
    
#if 0
    void dump_stack_lunwind(int fd, void *sigarg)
    {
      unw_cursor_t cursor; unw_context_t uc;
      unw_word_t ip, sp;

      unw_getcontext(&uc);
      unw_init_local(&cursor, &uc);
      if (sigarg) {
          const ucontext_t *signal_ucontext = (const ucontext_t *)sigarg;
          const struct sigcontext *signal_mcontext = &(signal_ucontext->uc_mcontext);
    #if 0
        unw_set_reg(&cursor, UNW_ARM_R0, signal_mcontext->arm_r0);
        unw_set_reg(&cursor, UNW_ARM_R1, signal_mcontext->arm_r1);
        unw_set_reg(&cursor, UNW_ARM_R2, signal_mcontext->arm_r2);
        unw_set_reg(&cursor, UNW_ARM_R3, signal_mcontext->arm_r3);
        unw_set_reg(&cursor, UNW_ARM_R4, signal_mcontext->arm_r4);
        unw_set_reg(&cursor, UNW_ARM_R5, signal_mcontext->arm_r5);
        unw_set_reg(&cursor, UNW_ARM_R6, signal_mcontext->arm_r6);
        unw_set_reg(&cursor, UNW_ARM_R7, signal_mcontext->arm_r7);
        unw_set_reg(&cursor, UNW_ARM_R8, signal_mcontext->arm_r8);
        unw_set_reg(&cursor, UNW_ARM_R9, signal_mcontext->arm_r9);
        unw_set_reg(&cursor, UNW_ARM_R10, signal_mcontext->arm_r10);
        unw_set_reg(&cursor, UNW_ARM_R11, signal_mcontext->arm_fp);
        unw_set_reg(&cursor, UNW_ARM_R12, signal_mcontext->arm_ip);
        unw_set_reg(&cursor, UNW_ARM_R13, signal_mcontext->arm_sp);
        unw_set_reg(&cursor, UNW_ARM_R14, signal_mcontext->arm_lr);
        unw_set_reg(&cursor, UNW_ARM_R15, signal_mcontext->arm_pc);

        unw_set_reg(&cursor, UNW_REG_IP, signal_mcontext->arm_pc);
        unw_set_reg(&cursor, UNW_REG_SP, signal_mcontext->arm_sp);
    #elif 0
        unw_set_reg(&cursor, UNW_AARCH64_X0, signal_mcontext->regs[0]);
        unw_set_reg(&cursor, UNW_AARCH64_X1, signal_mcontext->regs[1]);
        unw_set_reg(&cursor, UNW_AARCH64_X2, signal_mcontext->regs[2]);
        unw_set_reg(&cursor, UNW_AARCH64_X3, signal_mcontext->regs[3]);
        unw_set_reg(&cursor, UNW_AARCH64_X4, signal_mcontext->regs[4]);
        unw_set_reg(&cursor, UNW_AARCH64_X5, signal_mcontext->regs[5]);
        unw_set_reg(&cursor, UNW_AARCH64_X6, signal_mcontext->regs[6]);
        unw_set_reg(&cursor, UNW_AARCH64_X7, signal_mcontext->regs[7]);
        unw_set_reg(&cursor, UNW_AARCH64_X8, signal_mcontext->regs[8]);
        unw_set_reg(&cursor, UNW_AARCH64_X9, signal_mcontext->regs[9]);
        unw_set_reg(&cursor, UNW_AARCH64_X10, signal_mcontext->regs[10]);
        unw_set_reg(&cursor, UNW_AARCH64_X11, signal_mcontext->regs[11]);
        unw_set_reg(&cursor, UNW_AARCH64_X12, signal_mcontext->regs[12]);
        unw_set_reg(&cursor, UNW_AARCH64_X13, signal_mcontext->regs[13]);
        unw_set_reg(&cursor, UNW_AARCH64_X14, signal_mcontext->regs[14]);
        unw_set_reg(&cursor, UNW_AARCH64_X15, signal_mcontext->regs[15]);
        unw_set_reg(&cursor, UNW_AARCH64_X16, signal_mcontext->regs[16]);
        unw_set_reg(&cursor, UNW_AARCH64_X17, signal_mcontext->regs[17]);
        unw_set_reg(&cursor, UNW_AARCH64_X18, signal_mcontext->regs[18]);
        unw_set_reg(&cursor, UNW_AARCH64_X19, signal_mcontext->regs[19]);
        unw_set_reg(&cursor, UNW_AARCH64_X20, signal_mcontext->regs[20]);
        unw_set_reg(&cursor, UNW_AARCH64_X21, signal_mcontext->regs[21]);
        unw_set_reg(&cursor, UNW_AARCH64_X22, signal_mcontext->regs[22]);
        unw_set_reg(&cursor, UNW_AARCH64_X23, signal_mcontext->regs[23]);
        unw_set_reg(&cursor, UNW_AARCH64_X24, signal_mcontext->regs[24]);
        unw_set_reg(&cursor, UNW_AARCH64_X25, signal_mcontext->regs[25]);
        unw_set_reg(&cursor, UNW_AARCH64_X26, signal_mcontext->regs[26]);
        unw_set_reg(&cursor, UNW_AARCH64_X27, signal_mcontext->regs[27]);
        unw_set_reg(&cursor, UNW_AARCH64_X28, signal_mcontext->regs[28]);
        unw_set_reg(&cursor, UNW_AARCH64_X29, signal_mcontext->regs[29]);
        unw_set_reg(&cursor, UNW_AARCH64_X30, signal_mcontext->regs[30]);
        unw_set_reg(&cursor, UNW_AARCH64_PC, signal_mcontext->pc);
        unw_set_reg(&cursor, UNW_AARCH64_SP, signal_mcontext->sp);
        unw_set_reg(&cursor, UNW_AARCH64_PSTATE, signal_mcontext->pstate);

        struct fpsimd_context *fpsdc = (struct fpsimd_context *)&signal_mcontext->__reserved;
        unw_set_reg(&cursor, UNW_AARCH64_FPSR, fpsdc->fpsr);
        unw_set_reg(&cursor, UNW_AARCH64_FPCR, fpsdc->fpcr);
    #endif

      }

      int foundSig = 0;
      while (unw_step(&cursor) > 0) {
        // Keep walking up until one after our signal handler's
        // frame since we don't care about the signal handler and
        // below!
        if (!foundSig && !unw_is_signal_frame(&cursor))
            continue;
        if (!foundSig) {
            foundSig = 1;
            continue;
        }

        unw_get_reg(&cursor, UNW_REG_IP, &ip);
        unw_get_reg(&cursor, UNW_REG_SP, &sp);
        write_uint64(fd, ip, 16);
        write_string(fd, "\n  stack pointer: ");
        write_uint64(fd, sp, 16);
        write_string(fd, "\n  ");

        Dl_info inf;
        uint64_t ipv = (uint64_t)ip;
        if (dladdr((void *)ip, &inf) == 0 || !inf.dli_sname) {
            write_string(fd, "(no known symbol - dynamic code)\n");
        } else {
            if (inf.dli_fname) {
                write_string(fd, inf.dli_fname);
            } else {
                write_string(fd, "(library unknown)");
            }
            write_string(fd, " ");
            write_string(fd, inf.dli_sname);
            write_string(fd, "\n    offset = ");
            write_uint64(fd, (ipv - (uint64_t)inf.dli_fbase), 16);
            //fprintf(f, "  offset = %" PRIx64 " %" PRIx64 " %" PRIx64"\n", (ipv - (uint64_t)inf.dli_saddr), (ipv - (uint64_t)inf.dli_fbase), ((uint64_t)inf.dli_saddr - (uint64_t)inf.dli_fbase));
        }
        write_string(fd, "\n");
      }
    }
#endif

    void cxx_term_handler()
    {
        if (handling_cxx_term || sighandler_in_progress)
            return;
        handling_cxx_term = true;
        
        int fd = open_crash_file();
        if (fd != -1) {
            write_preface(fd);
            write_string(fd, "********** Stack trace follows:\n\n");
            dump_stack(fd, NULL);
            write_string(fd, "\n\n********** End of stack trace\n");
            
            // Check for exception info (gcc/llvm extension)
            std::type_info *type = abi::__cxa_current_exception_type();
            write_string(fd, "***** C++ Exception Info ******\n");
            if (type) {
                write_string(fd, "Exception type: ");
                write_string(fd, type->name());
                write_string(fd, "\n");
            }
            try {
                // Rethrow pending exception
                throw;
            } catch (std::exception &ex) {
                write_string(fd, "std::exception message: ");
                write_string(fd, ex.what());
                write_string(fd, "\n");
            } catch (...) {
                write_string(fd, "unknown exception type, cannot dump meaningful message\n");
            }
            
            close_crash_file(fd);
        }

        cxx_ex_handled = true;
        handling_cxx_term = false;
        
        // remove our handler
        std::set_terminate(NULL);

        // chain up to prior handler
        if (old_cxx_handler)
            old_cxx_handler();
        

    }





}



extern "C" {


static void jnicrashsig(int sig, siginfo_t *si, void *arg)
{
    if (sighandler_in_progress || handling_cxx_term) {
        return;
    }
    
    sighandler_in_progress = true;

    if (!cxx_ex_handled) {
        int fd = open_crash_file();
        if (fd != -1) {
            write_preface(fd);
            write_string(fd, "\nSignal caught: ");
            if (sig == SIGSEGV)
                write_string(fd, "SIGSEGV\n\n");
            else
                write_string(fd, "SIGABRT\n\n");
            
            write_string(fd, "********** Stack trace follows:\n\n");
            dump_stack(fd, arg);
            write_string(fd, "\n\n********** End of stack trace\n");
            close_crash_file(fd);
        }
    }

    if (sig == SIGSEGV && oldhandler_segf_set) {
        call_old_or_exit(oldhandler_segf, si, arg);
    } else if (sig == SIGABRT && oldhandler_abrt_set) {
        call_old_or_exit(oldhandler_abrt, si, arg);
    } else {
        // We should only be getting invoked for abrt/seg fault!
        exit(1);
    }

    sighandler_in_progress = false;
}


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    struct sigaction sa;
    memset(&sa, 0, sizeof(struct sigaction));
    sigemptyset(&sa.sa_mask);
    sa.sa_sigaction = jnicrashsig;
    sa.sa_flags = SA_SIGINFO;

    // This can be used instead of sigaction to install directly to kernel
    // and bypass bionic's libc impl. The bionic impl eats signals
    // that it cares about (including the ones we want) and, on some
    // versions, doesn't let us even see it.  But since it's a direct
    // syscall, it's prone to not being futureproof and different on different
    // architectures.
    //syscall(__NR_sigaction, SIGSEGV, &sa, &oldhandler);

    // Handle others? SIGILL SIGFPE SIGBUS ?
    if (sigaction(SIGSEGV, &sa, &oldhandler_segf) == 0) {
        oldhandler_segf_set = true;
    }

    if (sigaction(SIGABRT, &sa, &oldhandler_abrt) == 0) {
        oldhandler_abrt_set = true;
    }


    old_cxx_handler = std::set_terminate(cxx_term_handler);
    
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved)
{
}

} // end extern "C"

//#endif

JNIEXPORT void JNICALL Java_com_atakmap_jnicrash_JNICrash_setSystemDetails
  (JNIEnv *env, jclass selfCls, jstring jlogdir, jstring jdetails)
{
//#if defined(__arm__) || defined(__aarch64__)


    // always clear previous
    if (reportPreface) {
        delete[] reportPreface;
        reportPreface = NULL;
    }
    if (crash_filename) {
        delete[] crash_filename;
        crash_filename = NULL;
    }

    // NULL will crash the VM
    if (jlogdir == NULL || jdetails == NULL)
        return;

    const char *details = env->GetStringUTFChars(jdetails, NULL);
    if (!details)
        return;

    const char *logdir = env->GetStringUTFChars(jlogdir, NULL);
    if (!logdir)
        return;
    
    size_t n = strlen(details);
    
    reportPreface = new char[n + 1];
    memcpy(reportPreface, details, n);
    reportPreface[n] = '\0';
    
    pid_t pid = getpid();
    std::ostringstream s;
    s << logdir;
    s << "/";
    s << "ATAKNativeCrash-";
    s << pid;
    s << ".txt";
    std::string ss(s.str());
    
    n = ss.length();
    // fix leak of previous string
    
    crash_filename = new char[n + 1];
    memcpy(crash_filename, ss.c_str(), n);
    crash_filename[n] = '\0';
    
    env->ReleaseStringUTFChars(jlogdir, logdir);
    env->ReleaseStringUTFChars(jdetails, details);
//#endif
}

