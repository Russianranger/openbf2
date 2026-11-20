// [TEMP] Includes the exact production logger block extracted by run.py.
#include <dlfcn.h>
#include <signal.h>
#include <stdint.h>
#include <ucontext.h>
#include <unwind.h>
#include <unistd.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <sys/wait.h>
#if defined(BF2_CRASH_TEST_SKIP_UNWIND)
// [TEMP] QEMU's signal trampoline cannot be walked by this NDK unwinder.
// The second pass isolates termination from that pre-existing diagnostic.
static _Unwind_Reason_Code qaSkipUnwind(_Unwind_Trace_Fn, void*) { return _URC_END_OF_STACK; }
#define _Unwind_Backtrace qaSkipUnwind
#endif
#include "logger.inc"

static int checks = 0;
static void check(bool ok, const char* name)
{
    ++checks;
    if (!ok) { std::fprintf(stderr, "FAIL %s\n", name); std::exit(1); }
}

static std::string crash(void (*fault)(), int expectedSignal)
{
    int channel[2];
    check(pipe(channel) == 0, "create child log pipe");
    const pid_t child = fork();
    check(child >= 0, "create isolated fault process");
    if (child == 0)
    {
        close(channel[0]);
        if (dup2(channel[1], STDERR_FILENO) < 0) _exit(2);
        close(channel[1]);
        installAndroidCrashReport();
        if (fault) fault(); else raise(SIGABRT);
        _exit(3);
    }
    close(channel[1]);
    std::string log;
    char buffer[1024];
    ssize_t count;
    while ((count = read(channel[0], buffer, sizeof(buffer))) > 0)
        log.append(buffer, size_t(count));
    close(channel[0]);
    int status = 0;
    check(waitpid(child, &status, 0) == child, "reap fault process");
    check(WIFSIGNALED(status) && WTERMSIG(status) == expectedSignal,
        "preserve fatal signal termination");
    std::printf("--- signal %d child log ---\n%s", expectedSignal, log.c_str());
    check(log.find("[CRASH] original pc=0x") == 0,
        "original register record precedes unsafe symbolization");
    check(log.find("[CRASH] signal " + std::to_string(expectedSignal)) != std::string::npos,
        "retain original signal diagnostic");
    check(log.find("[CRASH] signal ") < log.find("[CRASH] original pc "),
        "signal/address/thread diagnostic precedes new loader lookup");
    return log;
}

int main(int argc, char** argv)
{
    if (argc != 2) return 2;
    void* library = dlopen(argv[1], RTLD_NOW);
    check(library != 0, "load private fault library");
    void (*fault)() = reinterpret_cast<void (*)()>(dlsym(library, "bf2_test_fault"));
    void* site = dlsym(library, "bf2_test_fault_site");
    check(fault && site, "resolve exact fault instruction");
    Dl_info where;
    check(dladdr(site, &where) != 0, "resolve expected module outside handler");
    const std::string log = crash(fault, SIGSEGV);
    char expected[512];
    std::snprintf(expected, sizeof(expected), "[CRASH] original pc=0x%016lx lr=0x",
        (unsigned long)reinterpret_cast<uintptr_t>(site));
    check(log.find(expected) == 0, "kernel context identifies faulting instruction, not handler");
    std::snprintf(expected, sizeof(expected), "[CRASH] original pc %s+0x%lx ",
        where.dli_fname,
        (unsigned long)(reinterpret_cast<uintptr_t>(site) - reinterpret_cast<uintptr_t>(where.dli_fbase)));
    check(log.find(expected) != std::string::npos, "correct fault DSO and ELF relative offset");
    unsigned long pc = 0, lr = 0;
    check(std::sscanf(log.c_str(), "[CRASH] original pc=0x%lx lr=0x%lx", &pc, &lr) == 2,
        "both raw registers parse");
    Dl_info caller;
    check(lr && dladdr(reinterpret_cast<void*>(lr), &caller), "saved LR resolves to caller");
    std::snprintf(expected, sizeof(expected), "[CRASH] original lr %s+0x%lx ",
        caller.dli_fname, lr - (unsigned long)reinterpret_cast<uintptr_t>(caller.dli_fbase));
    check(log.find(expected) != std::string::npos, "correct caller module and LR offset");
#if defined(BF2_CRASH_TEST_SKIP_UNWIND)
    const std::string aborted = crash(0, SIGABRT);
    check(aborted.find("[CRASH] original pc ") != std::string::npos,
        "original context is also reported for SIGABRT");
#endif
    dlclose(library);
    std::printf("PASS %d Android fatal-context checks\n", checks);
}
