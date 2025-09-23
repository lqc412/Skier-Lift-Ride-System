output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer."
  value       = aws_lb.this.dns_name
}

output "asg_name" {
  description = "Name of the Auto Scaling Group hosting the servlet tier."
  value       = aws_autoscaling_group.this.name
}

output "target_group_arn" {
  description = "ARN of the Application Load Balancer target group."
  value       = aws_lb_target_group.this.arn
}
