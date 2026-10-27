package com.aikeybridge.shell;

interface IShellService {
    void destroy() = 16777114;

    String exec(String command) = 1;
}
