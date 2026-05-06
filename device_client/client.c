#include <arpa/inet.h>
#include <netinet/in.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#define DEFAULT_SERVER_PORT 9999
#define BUFFER_SIZE 1024

static int send_text(int socket_fd, const char *text)
{
    return send(socket_fd, text, strlen(text), 0);
}

static void *recv_thread(void *arg)
{
    int socket_fd = *((int *)arg);

    while (1) {
        char buf[BUFFER_SIZE] = {0};
        int ret = recv(socket_fd, buf, sizeof(buf) - 1, 0);

        if (ret <= 0) {
            printf("\n[连接状态]: 与服务器断开连接\n");
            break;
        }

        printf("\n[收到指令]: %s\n", buf);

        if (strstr(buf, "openled") != NULL) {
            printf("[执行动作]: LED 打开\n");
            send_text(socket_fd, "LED:ON");
            printf("[上报状态]: LED:ON\n");
        } else if (strstr(buf, "closeled") != NULL) {
            printf("[执行动作]: LED 关闭\n");
            send_text(socket_fd, "LED:OFF");
            printf("[上报状态]: LED:OFF\n");
        } else {
            printf("[提示]: 未匹配到 LED 控制指令\n");
        }

        printf("\n手动发送数据（输入 quit 退出）: ");
        fflush(stdout);
    }

    return NULL;
}

int main(int argc, char *argv[])
{
    const char *server_addr_text = "127.0.0.1";
    int server_port = DEFAULT_SERVER_PORT;

    if (argc >= 2) {
        server_addr_text = argv[1];
    }
    if (argc >= 3) {
        server_port = atoi(argv[2]);
    }
    if (server_port <= 0 || server_port > 65535) {
        printf("端口范围应为 1-65535\n");
        return 1;
    }

    int socket_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (socket_fd == -1) {
        perror("socket");
        return 1;
    }

    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons((uint16_t)server_port);
    server_addr.sin_addr.s_addr = inet_addr(server_addr_text);

    if (connect(socket_fd, (struct sockaddr *)&server_addr, sizeof(server_addr)) == -1) {
        perror("connect");
        printf("连接服务器失败，请检查公网 IP、端口和云服务器防火墙规则。\n");
        close(socket_fd);
        return 1;
    }

    printf("===== 设备终端已连接到服务器 %s:%d =====\n", server_addr_text, server_port);
    printf("等待手机/PC 控制端发送 openled 或 closeled...\n\n");

    pthread_t thread;
    if (pthread_create(&thread, NULL, recv_thread, &socket_fd) != 0) {
        perror("pthread_create");
        close(socket_fd);
        return 1;
    }

    while (1) {
        char buf[BUFFER_SIZE] = {0};

        printf("手动发送数据（输入 quit 退出）: ");
        fflush(stdout);

        if (fgets(buf, sizeof(buf), stdin) == NULL) {
            break;
        }

        buf[strcspn(buf, "\n")] = '\0';
        if (strlen(buf) == 0) {
            continue;
        }
        if (strcmp(buf, "quit") == 0) {
            break;
        }

        send_text(socket_fd, buf);
    }

    close(socket_fd);
    return 0;
}
