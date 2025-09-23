output "vpc_id" {
  description = "ID of the created VPC."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "IDs for the public subnets."
  value       = [for subnet in aws_subnet.public : subnet.id]
}

output "private_subnet_ids" {
  description = "IDs for the private subnets."
  value       = [for subnet in aws_subnet.private : subnet.id]
}

output "alb_security_group_id" {
  description = "Security group ID assigned to the Application Load Balancer."
  value       = aws_security_group.alb.id
}

output "app_security_group_id" {
  description = "Security group ID assigned to the servlet Auto Scaling Group."
  value       = aws_security_group.app.id
}

output "consumer_security_group_id" {
  description = "Security group ID assigned to the consumer Auto Scaling Group."
  value       = aws_security_group.consumer.id
}

output "data_security_group_id" {
  description = "Security group ID assigned to RabbitMQ/Redis data services."
  value       = aws_security_group.data.id
}
