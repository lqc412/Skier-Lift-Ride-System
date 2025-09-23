output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer fronting the servlet tier."
  value       = module.app_server.alb_dns_name
}

output "app_asg_name" {
  description = "Name of the Auto Scaling Group running the servlet tier."
  value       = module.app_server.asg_name
}

output "consumer_asg_name" {
  description = "Name of the Auto Scaling Group processing RabbitMQ messages."
  value       = module.consumer.asg_name
}

output "rabbitmq_endpoint" {
  description = "AMQP endpoint or private IP address for RabbitMQ connections."
  value       = module.messaging.rabbitmq_endpoint
}

output "rabbitmq_management_url" {
  description = "Console URL for RabbitMQ management (if available)."
  value       = module.messaging.rabbitmq_management_url
}

output "rabbitmq_username" {
  description = "Username applications should use when connecting to RabbitMQ."
  value       = var.rabbitmq_user
}

output "rabbitmq_password" {
  description = "Password applications should use when connecting to RabbitMQ."
  value       = var.rabbitmq_password
  sensitive   = true
}

output "redis_endpoint" {
  description = "Hostname for Redis connections."
  value       = module.messaging.redis_endpoint
}

output "redis_uri" {
  description = "Redis connection URI exported for application configuration."
  value       = module.messaging.redis_uri
}

output "queue_name" {
  description = "RabbitMQ queue name shared between producers and consumers."
  value       = var.queue_name
}

output "client1_baseurl" {
  description = "Base URL for the phase-one client."
  value       = var.client1_baseurl
}

output "client2_baseurl" {
  description = "Base URL for the high-throughput client."
  value       = var.client2_baseurl
}
