# Build the AI plug-in from the same engine revision and libc++ ABI as the Android client.
MAKEFILE_PATH := $(abspath $(lastword $(MAKEFILE_LIST)))
COMPONENT_DIR := $(dir $(MAKEFILE_PATH))
SOURCE_ROOT ?= $(abspath $(COMPONENT_DIR)/../..)
OUT ?= $(CURDIR)/build/android-aidll
ANDROID_API ?= 28
HOST_TAG ?= linux-x86_64
TOOLCHAIN := $(ANDROID_NDK_HOME)/toolchains/llvm/prebuilt/$(HOST_TAG)/bin
CXX := $(TOOLCHAIN)/aarch64-linux-android$(ANDROID_API)-clang++
BF2 := $(SOURCE_ROOT)/Code/BF2
MANIFEST := $(SOURCE_ROOT)/Code/Build/aidll_sources.txt
SOURCES := $(shell sed -E '/^[[:space:]]*(#|$$)/d' $(MANIFEST))
OBJECTS := $(SOURCES:%.cpp=$(OUT)/%.o)
EXPORTS := $(SOURCE_ROOT)/Code/AidllAbi/aidll-posix.map
COMMON_FLAGS := -std=gnu++14 -O1 -DNDEBUG -fPIC -fno-strict-aliasing -fwrapv -fno-delete-null-pointer-checks -ffp-contract=off -fno-omit-frame-pointer -fms-extensions -Wno-narrowing -Wno-unknown-pragmas -Wno-multichar -Wno-deprecated-declarations -Wno-writable-strings
COMMON_FLAGS += -DBF2_PLATFORM_POSIX -include$(BF2)/Core/BuildConfig.h -include$(BF2)/Core/PlatformCompat.h
COMMON_FLAGS += -I$(BF2)/External/python-embedded/linux -I$(BF2)/External/python-embedded/py-src-2.3.4/Include

FLAGS := $(COMMON_FLAGS) -DDEDICATED=1 -DBF2_IMAGE_AIDLL=1

.PHONY: all
all: $(OUT)/libaidll.so

$(OUT)/libaidll.so: $(OBJECTS) $(EXPORTS) $(MANIFEST)
	$(CXX) -shared $(OBJECTS) -Wl,--no-undefined -Wl,--version-script=$(EXPORTS) -Wl,-soname,libaidll.so -Wl,-z,max-page-size=16384 -o $@

$(OUT)/%.o: $(BF2)/%.cpp $(MAKEFILE_PATH)
	@mkdir -p $(@D)
	$(CXX) $(FLAGS) -MMD -MP -c $< -o $@

-include $(OBJECTS:.o=.d)

.PHONY: loader-check
loader-check: $(OUT)/aidll-loader $(OUT)/libaidll.so

$(OUT)/aidll-loader: $(SOURCE_ROOT)/Code/Tests/aidll/posix_loader.cpp $(BF2)/Core/PluginDll.cpp
	@mkdir -p $(@D)
	$(CXX) $(FLAGS) $^ -Wl,-z,max-page-size=16384 -ldl -o $@

# ENGINE_DIR must contain the integration build's libmain.so. The test uses that
# actual client's registry and console, with the shared Android libc++ runtime.
.PHONY: client-handshake
client-handshake: $(OUT)/client-aidll-handshake $(OUT)/libaidll.so

$(OUT)/client-aidll-handshake: $(COMPONENT_DIR)/host_handshake.cpp $(ENGINE_DIR)/libmain.so
	@mkdir -p $(@D)
	$(CXX) $(COMMON_FLAGS) -DDEDICATED=0 -I$(BF2) $< -L$(ENGINE_DIR) -lmain -Wl,--allow-shlib-undefined -ldl -o $@
