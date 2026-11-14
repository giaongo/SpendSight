resource "verda_ssh_key" "team" {
  for_each = var.team_ssh_keys

  name       = "spendsight-${each.key}"
  public_key = trimspace(each.value)
}

resource "verda_startup_script" "bootstrap" {
  name = "spendsight-bootstrap"
  script = templatefile("${path.module}/startup.sh", {
    team_cidrs      = join(" ", var.team_cidrs)
    web_open_to_all = var.web_open_to_all
    repo_url        = var.repo_url
    repo_branch     = var.repo_branch
  })
}

resource "verda_instance" "api" {
  hostname      = var.hostname
  description   = "SpendSight API: Caddy + FastAPI + SQLite index on /data"
  location      = var.location
  instance_type = var.instance_type
  image         = var.image

  # Spot is cheaper but can be reclaimed at any time; see var.spot.
  is_spot  = var.spot
  contract = var.spot ? "SPOT" : "PAY_AS_YOU_GO"

  ssh_key_ids       = [for k in verda_ssh_key.team : k.id]
  startup_script_id = verda_startup_script.bootstrap.id

  os_volume = {
    name = "${var.hostname}-os"
    size = var.os_disk_size_gb
    type = var.os_disk_type
    # Keep the disk (and /data on it) if a spot VM is reclaimed.
    on_spot_discontinue = var.spot ? "keep_detached" : null
  }

  timeouts = {
    create = "20m"
  }
}
