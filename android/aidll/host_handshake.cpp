// Exercises the real host registry and AI plugin across an Android ELF boundary.
#include "Core/ClassManager.h"
#include "Core/ConsoleObjects.h"
#include "AI/AIDLL/AI/Main/BF2AI.h"
#include <cstdio>
#include <dlfcn.h>

using namespace dice::hfe;

static int checkCycle(ClassManager& registry, ConsoleObjects& console)
{
    void* library = dlopen("libaidll.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) {
        std::fprintf(stderr, "AI library missing: %s\n", dlerror());
        return 1;
    }
    auto init = reinterpret_cast<bool (*)(ClassManager*)>(dlsym(library, "initDll"));
    auto deinit = reinterpret_cast<void (*)()>(dlsym(library, "deinitDll"));
    if (!init || !deinit) return 2;

    // Only the console is registered in this isolated host. The plugin still registers its
    // module, console commands and AI singleton, but must report the missing game services.
    if (init(&registry)) return 3;
    auto* ai = static_cast<ai::IAI*>(registry.getSimpleSingleton("AI"));
    if (!ai || ai->isInitialized() || ai->isShuttingDown()) return 4;
    const auto count = console.getConsoleObjects().size();
    if (!count) return 5;
    deinit();
    if (registry.getSimpleSingleton("AI") || !console.getConsoleObjects().empty()) return 6;
    if (dlclose(library) != 0) return 7;
    std::printf("AI host handshake passed: %zu console commands registered and removed; "
                "uninitialized AI status queried across the library boundary.\n", count);
    return 0;
}

int main()
{
    ClassManager registry;
    ConsoleObjects console;
    IBase* consoleService = reinterpret_cast<IBase*>(&console);
    registry.registerSingleton({"MainConsoleObjects", &consoleService, 0, 0, 4});

    for (int i = 0; i < 2; ++i) {
        const int status = checkCycle(registry, console);
        if (status) {
            std::fprintf(stderr, "AI host handshake failed in cycle %d, step %d.\n", i + 1, status);
            return status;
        }
    }
    return 0;
}
