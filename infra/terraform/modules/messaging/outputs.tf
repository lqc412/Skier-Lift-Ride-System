locals {
  managed_rabbitmq_amqp = try(aws_mq_broker.rabbitmq[0].amqp_endpoints[0], null)
  managed_rabbitmq_host = local.managed_rabbitmq_amqp != null ? element(split(":", element(split("//", local.managed_rabbitmq_amqp), 1)), 0) : null
  managed_rabbitmq_port = local.managed_rabbitmq_amqp != null ? tonumber(element(split(":", element(split("//", local.managed_rabbitmq_amqp), 1)), 1)) : null

  self_managed_rabbitmq_ip = try(aws_instance.rabbitmq[0].private_ip, null)
  self_managed_rabbitmq_host = local.self_managed_rabbitmq_ip
  self_managed_rabbitmq_port = local.self_managed_rabbitmq_ip != null ? 5672 : null

  managed_redis_endpoint = try(aws_elasticache_replication_group.redis[0].primary_endpoint_address, null)
  self_managed_redis_ip = try(aws_instance.redis[0].private_ip, null)

  rabbitmq_host = coalesce(local.managed_rabbitmq_host, local.self_managed_rabbitmq_host)
  rabbitmq_port = coalesce(local.managed_rabbitmq_port, local.self_managed_rabbitmq_port, 5672)

  rabbitmq_requires_ssl = local.managed_rabbitmq_amqp != null

  redis_host = coalesce(local.managed_redis_endpoint, local.self_managed_redis_ip)
}

output "rabbitmq_endpoint" {
  description = "Complete RabbitMQ endpoint string (including host and port)."
  value = local.managed_rabbitmq_amqp != null ? local.managed_rabbitmq_amqp : format("amqp://%s:%d", local.self_managed_rabbitmq_host, local.rabbitmq_port)
}

output "rabbitmq_host" {
  description = "Hostname or private IP used by applications to reach RabbitMQ."
  value       = local.rabbitmq_host
}

output "rabbitmq_port" {
  description = "Port exposed by RabbitMQ."
  value       = tostring(local.rabbitmq_port)
}

output "rabbitmq_requires_ssl" {
  description = "Indicates whether clients must establish an SSL/TLS connection to RabbitMQ."
  value       = local.rabbitmq_requires_ssl
}

output "rabbitmq_management_url" {
  description = "URL for the RabbitMQ management console when available."
  value       = try(aws_mq_broker.rabbitmq[0].instances[0].console_url, null)
}

output "redis_endpoint" {
  description = "Hostname for Redis connections."
  value       = local.redis_host
}

output "redis_uri" {
  description = "Redis URI constructed from the resolved endpoint."
  value       = local.redis_host != null ? format("redis://%s:6379", local.redis_host) : null
}
