"""itpops 工作流层 —— 把多步 SOP 声明成一条命令。

runner.py       Step / Flow 数据结构与执行器（预演、写门、失败即断、变量传递）
definitions.py  backend / frontend / jvm 三条流程的步骤声明
wizard.py      交互式向导（问答生成命令，NEVER 执行写操作）

纪律：步骤只能是 `itpops` 子命令或本机构建命令（exec），NEVER 直接调其它特性包的
内部函数——所有调用都要经过唯一入口，才能共享全局参数 / JSON 信封 / 退出码。
"""
