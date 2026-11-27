cmake_minimum_required(VERSION 3.22.1)
project("uinput_helper" C)

add_library(uinput_helper SHARED
    uinput_helper.c)

find_library(log-lib log)
target_link_libraries(uinput_helper ${log-lib})
