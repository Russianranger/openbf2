// [TEMP] A controlled fault in a private QA DSO; never linked into the game.
extern "C" __attribute__((noinline)) void bf2_test_fault()
{
    asm volatile(
        "mov x0, #1\n"
        ".global bf2_test_fault_site\n"
        "bf2_test_fault_site:\n"
        "str xzr, [x0]\n"
        : : : "x0", "memory");
}
