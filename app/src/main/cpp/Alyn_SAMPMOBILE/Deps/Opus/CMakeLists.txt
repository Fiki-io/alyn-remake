project(opus)

add_library(${PROJECT_NAME} INTERFACE)

set(libopus_path "${CMAKE_CURRENT_SOURCE_DIR}/lib/${ANDROID_ABI}/libopus.a")
add_library(libopus STATIC IMPORTED GLOBAL)
set_target_properties(libopus PROPERTIES IMPORTED_LOCATION ${libopus_path})

target_link_libraries(${PROJECT_NAME} INTERFACE
        libopus
)

target_include_directories(${PROJECT_NAME} INTERFACE
        "${CMAKE_CURRENT_SOURCE_DIR}/include"
)
