variable "name" {
  description = "Name prefix for created resources."
  type        = string
}

variable "vpc_id" {
  description = "VPC identifier."
  type        = string
}

variable "alb_subnet_ids" {
  description = "Subnet IDs for the load balancer."
  type        = list(string)
}

variable "instance_subnet_ids" {
  description = "Subnet IDs for the application Auto Scaling Group."
  type        = list(string)
}

variable "alb_security_group_id" {
  description = "Security group ID assigned to the load balancer."
  type        = string
}

variable "instance_security_group_id" {
  description = "Security group ID assigned to the application instances."
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type for the servlet tier."
  type        = string
}

variable "desired_capacity" {
  description = "Desired number of instances in the Auto Scaling Group."
  type        = number
}

variable "min_size" {
  description = "Minimum number of instances in the Auto Scaling Group."
  type        = number
}

variable "max_size" {
  description = "Maximum number of instances in the Auto Scaling Group."
  type        = number
}

variable "artifact_url" {
  description = "URL pointing at the servlet WAR artifact."
  type        = string
}

variable "queue_name" {
  description = "RabbitMQ queue name for readiness probes and logging."
  type        = string
}

variable "environment_variables" {
  description = "Key/value map rendered into the instance environment."
  type        = map(string)
}

variable "lb_health_check_path" {
  description = "HTTP path used for ALB health checks."
  type        = string
  default     = "/skiers/1/vertical"
}

variable "ami_ssm_parameter_name" {
  description = "SSM parameter that stores the desired AMI ID."
  type        = string
  default     = "/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2"
}

variable "tags" {
  description = "Additional tags applied to all resources."
  type        = map(string)
  default     = {}
}
