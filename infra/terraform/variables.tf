variable "aws_region" {
  description = "AWS region where all resources will be created."
  type        = string
}

variable "project" {
  description = "Project prefix used for tagging and resource names."
  type        = string
  default     = "skier-lift-ride"
}

variable "environment" {
  description = "Short environment name such as dev, staging, or prod."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the primary VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for the public subnets that host the ALB and NAT gateway."
  type        = list(string)
  default     = [
    "10.0.0.0/24",
    "10.0.1.0/24",
  ]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for the private subnets that host the application, consumers, and data services."
  type        = list(string)
  default     = [
    "10.0.10.0/24",
    "10.0.11.0/24",
  ]
}

variable "az_count" {
  description = "Number of availability zones to use for subnets and deployments."
  type        = number
  default     = 2
}

variable "app_instance_type" {
  description = "EC2 instance type for the application Auto Scaling Group."
  type        = string
  default     = "t3.small"
}

variable "consumer_instance_type" {
  description = "EC2 instance type for the consumer Auto Scaling Group."
  type        = string
  default     = "t3.small"
}

variable "app_desired_capacity" {
  description = "Desired capacity for the application Auto Scaling Group."
  type        = number
  default     = 2
}

variable "app_min_size" {
  description = "Minimum number of instances in the application Auto Scaling Group."
  type        = number
  default     = 2
}

variable "app_max_size" {
  description = "Maximum number of instances in the application Auto Scaling Group."
  type        = number
  default     = 6
}

variable "consumer_desired_capacity" {
  description = "Desired capacity for the consumer Auto Scaling Group."
  type        = number
  default     = 2
}

variable "consumer_min_size" {
  description = "Minimum number of instances in the consumer Auto Scaling Group."
  type        = number
  default     = 1
}

variable "consumer_max_size" {
  description = "Maximum number of instances in the consumer Auto Scaling Group."
  type        = number
  default     = 4
}

variable "app_artifact_url" {
  description = "URL (HTTP/S or S3 pre-signed) pointing at the latest servlet WAR artifact."
  type        = string
}

variable "consumer_artifact_url" {
  description = "URL pointing at the latest RabbitMQ consumer executable JAR."
  type        = string
}

variable "queue_name" {
  description = "RabbitMQ queue name shared between the servlet and consumer."
  type        = string
  default     = "SkierServletPostQueue"
}

variable "client1_baseurl" {
  description = "Base URL configured for the phase-one client."
  type        = string
  default     = "http://localhost:8080/Server_war_exploded"
}

variable "client2_baseurl" {
  description = "Base URL configured for the high-throughput client."
  type        = string
  default     = "http://localhost:8080/Server2_war"
}

variable "rabbitmq_mode" {
  description = "Deployment mode for RabbitMQ. Accepted values: managed, self_managed."
  type        = string
  default     = "managed"
  validation {
    condition     = contains(["managed", "self_managed"], var.rabbitmq_mode)
    error_message = "rabbitmq_mode must be either 'managed' or 'self_managed'."
  }
}

variable "rabbitmq_engine_version" {
  description = "RabbitMQ engine version when using Amazon MQ."
  type        = string
  default     = "3.11.20"
}

variable "rabbitmq_instance_type" {
  description = "Instance type for the Amazon MQ RabbitMQ broker."
  type        = string
  default     = "mq.t3.small"
}

variable "rabbitmq_user" {
  description = "Admin username for RabbitMQ (used for both managed and self-managed modes)."
  type        = string
  default     = "skieradmin"
}

variable "rabbitmq_password" {
  description = "Admin password for RabbitMQ (used for both managed and self-managed modes)."
  type        = string
  sensitive   = true
}

variable "redis_mode" {
  description = "Deployment mode for Redis. Accepted values: managed, self_managed."
  type        = string
  default     = "managed"
  validation {
    condition     = contains(["managed", "self_managed"], var.redis_mode)
    error_message = "redis_mode must be either 'managed' or 'self_managed'."
  }
}

variable "redis_node_type" {
  description = "Node type for Amazon ElastiCache when redis_mode is managed."
  type        = string
  default     = "cache.t3.small"
}

variable "redis_engine_version" {
  description = "Redis engine version used when redis_mode is managed."
  type        = string
  default     = "7.0"
}

variable "self_managed_data_instance_type" {
  description = "Instance type used when RabbitMQ or Redis are deployed in self-managed mode."
  type        = string
  default     = "t3.small"
}

variable "tags" {
  description = "Common tags applied to all resources."
  type        = map(string)
  default     = {}
}
