variable "name_prefix" {
  description = "Prefix applied to network resource names."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
}

variable "public_subnet_cidrs" {
  description = "List of CIDR blocks for public subnets."
  type        = list(string)
}

variable "private_subnet_cidrs" {
  description = "List of CIDR blocks for private subnets."
  type        = list(string)
}

variable "az_count" {
  description = "Number of availability zones to use."
  type        = number
}

variable "tags" {
  description = "Tags applied to network resources."
  type        = map(string)
  default     = {}
}
