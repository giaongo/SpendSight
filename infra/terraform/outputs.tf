output "instance_id" {
  description = "Verda instance id."
  value       = verda_instance.api.id
}

output "public_ip" {
  description = "Public IPv4 of the VM. Feeds the Caddy name (OPS-03, e.g. api.<ip>.sslip.io) and the deploy host (OPS-09)."
  value       = verda_instance.api.ip
}

output "ssh" {
  description = "SSH command for the VM (key only)."
  value       = "ssh root@${verda_instance.api.ip}"
}

output "price_per_hour" {
  description = "Hourly price Verda reports for the instance."
  value       = verda_instance.api.price_per_hour
}
