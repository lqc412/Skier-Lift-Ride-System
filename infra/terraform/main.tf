terraform {
  required_version = ">= 1.4.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

locals {
  name_prefix = "${var.project}-${var.environment}"
  common_tags = merge(
    {
      Project     = var.project
      Environment = var.environment
    },
    var.tags,
  )
}

module "network" {
  source               = "./modules/network"
  name_prefix          = local.name_prefix
  vpc_cidr             = var.vpc_cidr
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
  az_count             = var.az_count
  tags                 = local.common_tags
}

module "messaging" {
  source                       = "./modules/messaging"
  name_prefix                  = local.name_prefix
  vpc_id                       = module.network.vpc_id
  private_subnet_ids           = module.network.private_subnet_ids
  security_group_id            = module.network.data_security_group_id
  rabbitmq_mode                = var.rabbitmq_mode
  rabbitmq_engine_version      = var.rabbitmq_engine_version
  rabbitmq_instance_type       = var.rabbitmq_instance_type
  rabbitmq_user                = var.rabbitmq_user
  rabbitmq_password            = var.rabbitmq_password
  redis_mode                   = var.redis_mode
  redis_node_type              = var.redis_node_type
  redis_engine_version         = var.redis_engine_version
  self_managed_instance_type   = var.self_managed_data_instance_type
  tags                         = local.common_tags
}

module "app_server" {
  source                     = "./modules/app_server"
  name                       = "${local.name_prefix}-app"
  vpc_id                     = module.network.vpc_id
  alb_subnet_ids             = module.network.public_subnet_ids
  instance_subnet_ids        = module.network.private_subnet_ids
  alb_security_group_id      = module.network.alb_security_group_id
  instance_security_group_id = module.network.app_security_group_id
  instance_type              = var.app_instance_type
  desired_capacity           = var.app_desired_capacity
  min_size                   = var.app_min_size
  max_size                   = var.app_max_size
  artifact_url               = var.app_artifact_url
  queue_name                 = var.queue_name
  environment_variables = {
    RABBITMQ_HOST            = module.messaging.rabbitmq_host
    RABBITMQ_PORT            = module.messaging.rabbitmq_port
    RABBITMQ_USERNAME        = var.rabbitmq_user
    RABBITMQ_PASSWORD        = var.rabbitmq_password
    RABBITMQ_USE_SSL         = module.messaging.rabbitmq_requires_ssl ? "true" : "false"
    REDIS_URI                = module.messaging.redis_uri
    QUEUE_NAME               = var.queue_name
    CLIENT1_BASEURL          = var.client1_baseurl
    CLIENT2_BASEURL          = var.client2_baseurl
    CLIENT2_RATE_LIMIT       = "5000"
    CLIENT2_FAILURE_THRESHOLD = "100"
    CLIENT2_CIRCUIT_BREAKER_TIMEOUT_MS = "10000"
  }
  tags = local.common_tags
}

module "consumer" {
  source               = "./modules/consumer"
  name                 = "${local.name_prefix}-consumer"
  instance_subnet_ids  = module.network.private_subnet_ids
  security_group_id    = module.network.consumer_security_group_id
  instance_type        = var.consumer_instance_type
  desired_capacity     = var.consumer_desired_capacity
  min_size             = var.consumer_min_size
  max_size             = var.consumer_max_size
  artifact_url         = var.consumer_artifact_url
  environment_variables = {
    RABBITMQ_HOST     = module.messaging.rabbitmq_host
    RABBITMQ_PORT     = module.messaging.rabbitmq_port
    RABBITMQ_USERNAME = var.rabbitmq_user
    RABBITMQ_PASSWORD = var.rabbitmq_password
    RABBITMQ_USE_SSL  = module.messaging.rabbitmq_requires_ssl ? "true" : "false"
    REDIS_URI         = module.messaging.redis_uri
    QUEUE_NAME        = var.queue_name
  }
  tags = local.common_tags
}
