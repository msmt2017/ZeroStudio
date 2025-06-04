cmake_minimum_required(VERSION 3.14.2) # As requested

# Project name
project(termux-bootstrap)

# Set C flags (if needed, though these are often set globally or per-target)
# set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c11 -Wall -Wextra") # Example

# Create a shared library
add_library(libtermux-bootstrap SHARED
    termux-bootstrap-zip.S
    termux-bootstrap.c
)

# Target include directories (if needed)
# target_include_directories(libtermux-bootstrap PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

# Target link libraries (if needed)
# target_link_libraries(libtermux-bootstrap other_library)