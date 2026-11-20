// Only the diagnostic formatting aliases normally provided by the host build.
#pragma once
#include <cstddef>
#include <cstdio>
#define _snprintf snprintf
#define _TRUNCATE (-1)
#define _snprintf_s(buffer, count, truncate, ...) snprintf(buffer, count, __VA_ARGS__)
