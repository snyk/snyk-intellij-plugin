provider "aws" {
  version                     = "~> 2.67"
  region                      = var.region
  access_key                  = var.access_key
  secret_key                  = var.secret_key
}

  resource "aws_iam_account_password_policy" "strict" {
  minimum_password_length        = 8
  max_password_age                = 3
}

resource "aws_security_group" "km_rds_sg" {
  name = "km_rds_sg"
  vpc_id = "1"

  # HTTP access from anywhere
  ingress {
    from_port = 5432
    to_port = 5432
    protocol = "tcp"
    cidr_blocks = [
      "0.0.0.0/0"]
  }
}
