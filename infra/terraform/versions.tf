terraform {
  required_version = ">= 1.6"

  required_providers {
    verda = {
      source  = "verda-cloud/verda"
      version = "~> 1.0"
    }
  }

  # OPS-11 moves state to a shared remote backend with locking.
  # Until then state is local and gitignored. Never commit it.
}

# Credentials come from VERDA_CLIENT_ID and VERDA_CLIENT_SECRET in the shell
# (or GitHub Secrets in CI). They never go in a .tf or .tfvars file.
provider "verda" {}
