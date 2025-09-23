output "asg_name" {
  description = "Name of the Auto Scaling Group running the RabbitMQ consumers."
  value       = aws_autoscaling_group.this.name
}
