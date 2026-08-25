project(opus)

set(opus_path "${CMAKE_CURRENT_SOURCE_DIR}/lib/${ANDROID_ABI}/libopus.a")
add_library(opus STATIC IMPORTED)
set_target_properties(opus PROPERTIES IMPORTED_LOCATION ${opus_path})

target_include_directories(opus INTERFACE
        "${CMAKE_CURRENT_SOURCE_DIR}/include"
)
