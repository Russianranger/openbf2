// Content-free loader smoke through the packaged engine's actual POSIX wrappers.
// Does not call plugin initDll, bind sockets, or establish gameplay coverage.
#include <cstdio>
#include <dlfcn.h>
extern void *LoadLibrary(const char *);
extern void *GetProcAddress(void *, const char *);
extern bool FreeLibrary(void *);
int main()
{
    const char *names[] = {".\\aidll.so", ".\\RendDX9.dll", ".\\SwiffPlayer.dll"};
    unsigned passed = 0;
    for (const char *name : names)
    {
        void *handle = LoadLibrary(name);
        if (!handle)
        {
            std::fprintf(stderr, "load %s: %s\n", name, dlerror());
            return 1;
        }
        const bool exports =
            GetProcAddress(handle, "initDll") && GetProcAddress(handle, "deinitDll");
        const bool privateAi =
            passed != 0 || !GetProcAddress(handle, "_ZN4dice3hfe12classManagerE");
        const bool closed = FreeLibrary(handle);
        if (!exports || !privateAi || !closed)
        {
            std::fprintf(stderr, "FAIL %s exports=%d privateAI=%d closed=%d\n", name, exports,
                         privateAi, closed);
            return 1;
        }
        ++passed;
        std::printf("PASS %s: loaded, handshake exports found, closed; initDll not called\n", name);
    }
    std::printf("Packaged engine plugin loader: %u/%u passed\n", passed, 3u);
    return 0;
}
