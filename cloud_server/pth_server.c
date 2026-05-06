#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#define SERVER_PORT 9999
#define MAX_CLIENTS 256
#define BUFFER_SIZE 1024

struct client_info {
    struct sockaddr_in addr;
    int fd;
    int active;
    pthread_t thread;
};

static struct client_info clients[MAX_CLIENTS];
static pthread_mutex_t clients_mutex = PTHREAD_MUTEX_INITIALIZER;

static int active_client_count(void)
{
    int count = 0;

    pthread_mutex_lock(&clients_mutex);
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (clients[i].active) {
            count++;
        }
    }
    pthread_mutex_unlock(&clients_mutex);

    return count;
}

static void remove_client(int fd)
{
    pthread_mutex_lock(&clients_mutex);
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (clients[i].active && clients[i].fd == fd) {
            clients[i].active = 0;
            close(clients[i].fd);
            clients[i].fd = -1;
            break;
        }
    }
    pthread_mutex_unlock(&clients_mutex);
}

static void broadcast_to_others(int sender_fd, const char *buf, ssize_t len)
{
    pthread_mutex_lock(&clients_mutex);
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (!clients[i].active || clients[i].fd == sender_fd) {
            continue;
        }

        ssize_t sent = send(clients[i].fd, buf, (size_t)len, 0);
        if (sent == -1) {
            printf("[!] 转发到 fd=%d 失败: %s\n", clients[i].fd, strerror(errno));
            close(clients[i].fd);
            clients[i].active = 0;
            clients[i].fd = -1;
        }
    }
    pthread_mutex_unlock(&clients_mutex);
}

static void *client_worker(void *arg)
{
    struct client_info *client = (struct client_info *)arg;
    int fd = client->fd;
    char ip[INET_ADDRSTRLEN] = {0};

    pthread_detach(pthread_self());
    inet_ntop(AF_INET, &client->addr.sin_addr, ip, sizeof(ip));

    while (1) {
        char buf[BUFFER_SIZE] = {0};
        ssize_t ret = recv(fd, buf, sizeof(buf) - 1, 0);

        if (ret == 0) {
            printf("[-] 客户端 %s:%d 主动断开\n", ip, ntohs(client->addr.sin_port));
            break;
        }
        if (ret < 0) {
            printf("[!] 接收客户端 %s:%d 数据失败: %s\n",
                   ip,
                   ntohs(client->addr.sin_port),
                   strerror(errno));
            break;
        }

        printf("[<] 来自 %s:%d 的数据: %.*s\n",
               ip,
               ntohs(client->addr.sin_port),
               (int)ret,
               buf);

        broadcast_to_others(fd, buf, ret);
        printf("[>] 已转发给其他在线客户端，当前在线 %d 个\n", active_client_count());
    }

    remove_client(fd);
    return NULL;
}

int main(void)
{
    int socket_fd;
    struct sockaddr_in server_addr;

    signal(SIGPIPE, SIG_IGN);
    memset(clients, 0, sizeof(clients));
    for (int i = 0; i < MAX_CLIENTS; i++) {
        clients[i].fd = -1;
    }

    socket_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (socket_fd == -1) {
        perror("socket");
        return 1;
    }

    int optval = 1;
    if (setsockopt(socket_fd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval)) == -1) {
        perror("setsockopt");
        close(socket_fd);
        return 1;
    }

    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    server_addr.sin_port = htons(SERVER_PORT);

    if (bind(socket_fd, (struct sockaddr *)&server_addr, sizeof(server_addr)) == -1) {
        perror("bind");
        close(socket_fd);
        return 1;
    }

    if (listen(socket_fd, 20) == -1) {
        perror("listen");
        close(socket_fd);
        return 1;
    }

    printf("===== 云数据处理转发服务器已启动 =====\n");
    printf("监听端口: %d\n", SERVER_PORT);
    printf("功能: 收到任意客户端数据后，转发给其他所有在线客户端\n\n");

    while (1) {
        struct sockaddr_in client_addr;
        socklen_t client_addr_len = sizeof(client_addr);
        int new_fd = accept(socket_fd, (struct sockaddr *)&client_addr, &client_addr_len);

        if (new_fd == -1) {
            perror("accept");
            continue;
        }

        int slot = -1;
        pthread_mutex_lock(&clients_mutex);
        for (int i = 0; i < MAX_CLIENTS; i++) {
            if (!clients[i].active) {
                slot = i;
                clients[i].addr = client_addr;
                clients[i].fd = new_fd;
                clients[i].active = 1;
                break;
            }
        }
        pthread_mutex_unlock(&clients_mutex);

        if (slot == -1) {
            printf("[!] 客户端数量已满，拒绝新连接\n");
            close(new_fd);
            continue;
        }

        char ip[INET_ADDRSTRLEN] = {0};
        inet_ntop(AF_INET, &client_addr.sin_addr, ip, sizeof(ip));
        printf("[+] 新客户端连接: IP=%s Port=%d 当前在线=%d\n",
               ip,
               ntohs(client_addr.sin_port),
               active_client_count());

        if (pthread_create(&clients[slot].thread, NULL, client_worker, &clients[slot]) != 0) {
            printf("[!] 创建客户端线程失败\n");
            remove_client(new_fd);
        }
    }

    close(socket_fd);
    return 0;
}
