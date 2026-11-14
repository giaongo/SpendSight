# Codes come from the Verda API: GET /v1/instance-types, /v1/images, /v1/locations.
# Changing instance_type, image, or os_disk_* replaces the VM. Re-sync /data
# afterwards (OPS-04).

variable "location" {
  description = "Verda location code. FIN-01, FIN-02 and FIN-03 are all in Finland (EU)."
  type        = string
  default     = "FIN-03"
}

variable "instance_type" {
  description = "Verda CPU instance type. CPU-TURIN.4V.16G is 4 vCPU and 16 GB RAM (AMD EPYC Turin). CPU.4V.16G had no capacity in FIN-01."
  type        = string
  default     = "CPU-TURIN.4V.16G"
}

variable "spot" {
  description = "Run as a spot instance: cheaper, but Verda can reclaim it at any time, e.g. mid-judging. The OS disk (and /data) is kept detached on reclaim. Changing this replaces the VM."
  type        = bool
  default     = false
}

variable "image" {
  description = "Verda OS image code."
  type        = string
  default     = "ubuntu-24.04"
}

variable "os_disk_size_gb" {
  description = "OS disk size in GB. /data (CSVs plus the SQLite index) lives on this disk. Verda rejected 40 GB for CPU-TURIN.4V.16G as too low."
  type        = number
  default     = 100

  validation {
    condition     = var.os_disk_size_gb >= 50
    error_message = "Use at least 50 GB: Verda rejects smaller OS disks, and the CSVs, index and Docker images must fit."
  }
}

variable "os_disk_type" {
  description = "Verda volume type for the OS disk. HDD can no longer be created."
  type        = string
  default     = "NVMe"
}

variable "hostname" {
  description = "Hostname of the VM."
  type        = string
  default     = "spendsight-api"
}

variable "team_ssh_keys" {
  description = "One public SSH key per team member, keyed by a short name, e.g. { giao = \"ssh-ed25519 AAAA...\" }."
  type        = map(string)

  validation {
    condition     = length(var.team_ssh_keys) > 0
    error_message = "Add at least one team SSH key, or nobody can log in."
  }

  validation {
    condition     = alltrue([for k in values(var.team_ssh_keys) : can(regex("^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521)) ", k))])
    error_message = "Each team_ssh_keys value must be an OpenSSH public key line."
  }
}

variable "team_cidrs" {
  description = "Team IPs that may reach port 22, as CIDRs, e.g. [\"203.0.113.7/32\"]. Also the only IPs that reach 80/443 when web_open_to_all is false."
  type        = list(string)

  validation {
    condition     = length(var.team_cidrs) > 0
    error_message = "Add at least one team CIDR, or SSH is closed to everyone."
  }

  validation {
    condition     = alltrue([for c in var.team_cidrs : can(cidrhost(c, 0)) && !strcontains(c, ":")])
    error_message = "Each team_cidrs entry must be an IPv4 CIDR such as 203.0.113.7/32."
  }
}

variable "web_open_to_all" {
  description = "Open 80/443 to everyone. Keep false until OPS-05 confirms the Junction data terms allow public hosting of /customers/*."
  type        = bool
  default     = false
}

variable "repo_url" {
  description = "Git URL the startup script clones to /opt/spendsight. The repo is public, so HTTPS needs no credentials."
  type        = string
  default     = "https://github.com/minhson0506/SpendSight.git"
}

variable "repo_branch" {
  description = "Branch the startup script checks out on first clone."
  type        = string
  default     = "master"
}
