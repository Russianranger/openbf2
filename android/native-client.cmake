# The portable client runs inside SDLActivity, which loads libmain.so and invokes SDL_main.
set(BF2_ANDROID_LIBDIR "" CACHE PATH "Directory with the Stage 3 arm64-v8a SDL3 and Python shared libraries")
set(BF2_ADRENOTOOLS_ROOT "" CACHE PATH "Checkout of libadrenotools with its linkernsbypass submodule")
if(BF2_ANDROID_LIBDIR AND BF2_ADRENOTOOLS_ROOT)
    add_subdirectory("${BF2_ADRENOTOOLS_ROOT}" "${CMAKE_BINARY_DIR}/adrenotools" EXCLUDE_FROM_ALL)
    add_library(bf2_android_client SHARED EXCLUDE_FROM_ALL
        $<TARGET_OBJECTS:bf2_client_objects>
        $<TARGET_OBJECTS:bf2_zlib_objects>
        $<TARGET_OBJECTS:bf2_tinyxml_objects>
        $<TARGET_OBJECTS:bf2_gamespy_objects>
        $<TARGET_OBJECTS:bf2_gamespy_sb_objects>
        $<TARGET_OBJECTS:bf2_gamespy_gp_objects>
        $<TARGET_OBJECTS:bf2_gamespy_cdkeyc_objects>)
    set_target_properties(bf2_android_client PROPERTIES LINKER_LANGUAGE CXX OUTPUT_NAME main)
    target_link_libraries(bf2_android_client PRIVATE
        "${BF2_ANDROID_LIBDIR}/libdice_py.so" "${BF2_ANDROID_LIBDIR}/libSDL3.so"
        adrenotools android log dl m)
    target_link_options(bf2_android_client PRIVATE -Wl,--no-undefined)
endif()
