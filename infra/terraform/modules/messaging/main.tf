data "aws_ssm_parameter" "ami" {
  name = var.ami_ssm_parameter_name
}

locals {
  rabbitmq_user_data = templatefile("${path.module}/user_data_rabbitmq.sh.tpl", {
    rabbitmq_user     = var.rabbitmq_user
    rabbitmq_password = var.rabbitmq_password
  })

  redis_user_data = file("${path.module}/user_data_redis.sh.tpl")

  tags = merge(var.tags, {
    Component = "messaging"
    Name      = "${var.name_prefix}-messaging"
  })
}

resource "aws_mq_broker" "rabbitmq" {
  count                = var.rabbitmq_mode == "managed" ? 1 : 0
  broker_name          = "${var.name_prefix}-rabbitmq"
  engine_type          = "RabbitMQ"
  engine_version       = var.rabbitmq_engine_version
  host_instance_type   = var.rabbitmq_instance_type
  publicly_accessible  = false
  security_groups      = [var.security_group_id]
  subnet_ids           = slice(var.private_subnet_ids, 0, length(var.private_subnet_ids))
  deployment_mode      = "SINGLE_INSTANCE"
  auto_minor_version_upgrade = true

  user {
    username = var.rabbitmq_user
    password = var.rabbitmq_password
  }

  logs {
    general = true
  }

  tags = merge(var.tags, {
    Component = "rabbitmq"
    Name      = "${var.name_prefix}-rabbitmq"
  })
}

resource "aws_instance" "rabbitmq" {
  count                       = var.rabbitmq_mode == "self_managed" ? 1 : 0
  ami                         = data.aws_ssm_parameter.ami.value
  instance_type               = var.self_managed_instance_type
  subnet_id                   = element(var.private_subnet_ids, 0)
  vpc_security_group_ids      = [var.security_group_id]
  associate_public_ip_address = false
  user_data                   = local.rabbitmq_user_data

  tags = merge(var.tags, {
    Component = "rabbitmq"
    Name      = "${var.name_prefix}-rabbitmq"
  })
}

resource "aws_elasticache_subnet_group" "redis" {
  count      = var.redis_mode == "managed" ? 1 : 0
  name       = "${var.name_prefix}-redis"
  subnet_ids = var.private_subnet_ids

  tags = merge(var.tags, {
    Component = "redis"
    Name      = "${var.name_prefix}-redis"
  })
}

resource "aws_elasticache_replication_group" "redis" {
  count                       = var.redis_mode == "managed" ? 1 : 0
  replication_group_id        = replace("${var.name_prefix}-redis", "_", "-")
  replication_group_description = "Redis backing store for the Skier Lift Ride system"
  engine                      = "redis"
  engine_version              = var.redis_engine_version
  node_type                   = var.redis_node_type
  number_cache_clusters       = 1
  port                        = 6379
  automatic_failover_enabled  = false
  subnet_group_name           = aws_elasticache_subnet_group.redis[0].name
  security_group_ids          = [var.security_group_id]

  tags = merge(var.tags, {
    Component = "redis"
    Name      = "${var.name_prefix}-redis"
  })
}

resource "aws_instance" "redis" {
  count                       = var.redis_mode == "self_managed" ? 1 : 0
  ami                         = data.aws_ssm_parameter.ami.value
  instance_type               = var.self_managed_instance_type
  subnet_id                   = element(var.private_subnet_ids, 0)
  vpc_security_group_ids      = [var.security_group_id]
  associate_public_ip_address = false
  user_data                   = local.redis_user_data

  tags = merge(var.tags, {
    Component = "redis"
    Name      = "${var.name_prefix}-redis"
  })
}
