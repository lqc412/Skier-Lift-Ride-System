variable "name_prefix" {
  description = "Prefix applied to messaging resources."
  type        = string
}

variable "vpc_id" {
  description = "VPC identifier used for messaging services."
  type        = string
}

variable "private_subnet_ids" {
  description = "Subnet IDs hosting the messaging services."
  type        = list(string)
}

variable "security_group_id" {
  description = "Security group shared by RabbitMQ and Redis."
  type        = string
}

variable "rabbitmq_mode" {
  description = "Deployment mode for RabbitMQ."
  type        = string
}

variable "rabbitmq_engine_version" {
  description = "RabbitMQ engine version when using Amazon MQ."
  type        = string
}

variable "rabbitmq_instance_type" {
  description = "Instance class used for the Amazon MQ broker."
  type        = string
}

variable "rabbitmq_user" {
  description = "RabbitMQ administrator username."
  type        = string
}

variable "rabbitmq_password" {
  description = "RabbitMQ administrator password."
  type        = string
  sensitive   = true
}

variable "redis_mode" {
  description = "Deployment mode for Redis."
  type        = string
}

variable "redis_node_type" {
  description = "Node type for Amazon ElastiCache."
  type        = string
}

variable "redis_engine_version" {
  description = "Redis engine version."
  type        = string
}

variable "self_managed_instance_type" {
  description = "Instance type used when deploying self-managed messaging nodes."
  type        = string
}

variable "ami_ssm_parameter_name" {
  description = "SSM parameter that stores the base AMI for self-managed instances."
  type        = string
  default     = "/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2"
}

variable "tags" {
  description = "Tags applied to all messaging resources."
  type        = map(string)
  default     = {}
}
