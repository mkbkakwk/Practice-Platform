package com.oj.runner.execution.linux;

public interface SandboxProcessLauncher {

    NsJailExecutionResult launch(NsJailInvocation invocation);
}
