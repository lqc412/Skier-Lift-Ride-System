variable "name" {
  description = "Name prefix for created resources."
  type        = string
}

variable "instance_subnet_ids" {
  description = "Subnets hosting the consumer Auto Scaling Group."
  type        = list(string)
}

variable "security_group_id" {
  description = "Security group assigned to consumer instances."
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type for the consumer workers."
  type        = string
}

variable "desired_capacity" {
  description = "Desired number of consumer instances."
  type        = number
}

variable "min_size" {
  description = "Minimum number of consumer instances."
  type        = number
}

variable "max_size" {
  description = "Maximum number of consumer instances."
  type        = number
}

variable "artifact_url" {
  description = "URL pointing to the consumer executable JAR."
  type        = string
}

variable "environment_variables" {
  description = "Environment variables written into the instance runtime."
  type        = map(string)
}

variable "ami_ssm_parameter_name" {
  description = "SSM parameter containing the base AMI ID."
  type        = string
  default     = "/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2"
}

variable "tags" {
  description = "Additional tags applied to resources."
  type        = map(string)
  default     = {}
}
