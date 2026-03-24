# 该命令以节点1为例
docker exec -it chat-ai-rabbitmq02 /bin/bash

#ram节点加入集群

rabbitmqctl stop_app
rabbitmqctl reset
rabbitmqctl join_cluster --ram chat-ai-rabbitmq01@chat-ai-rabbitmq01
rabbitmqctl start_app