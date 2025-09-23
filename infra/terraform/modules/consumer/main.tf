data "aws_ssm_parameter" "ami" {
  name = var.ami_ssm_parameter_name
}

locals {
  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    artifact_url          = var.artifact_url
    environment_variables = var.environment_variables
  })

  tags = merge(var.tags, {
    Component = "consumer"
    Name      = var.name
  })
}

resource "aws_launch_template" "this" {
  name_prefix   = "${var.name}-lt-"
  image_id      = data.aws_ssm_parameter.ami.value
  instance_type = var.instance_type

  user_data = base64encode(local.user_data)

  vpc_security_group_ids = [var.security_group_id]

  tag_specifications {
    resource_type = "instance"
    tags          = local.tags
  }

  tag_specifications {
    resource_type = "volume"
    tags          = local.tags
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_autoscaling_group" "this" {
  name                = "${var.name}-asg"
  desired_capacity    = var.desired_capacity
  min_size            = var.min_size
  max_size            = var.max_size
  vpc_zone_identifier = var.instance_subnet_ids

  launch_template {
    id      = aws_launch_template.this.id
    version = "$Latest"
  }

  tag {
    key                 = "Name"
    value               = var.name
    propagate_at_launch = true
  }

  lifecycle {
    create_before_destroy = true
  }
}
