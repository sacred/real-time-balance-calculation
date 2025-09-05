echo "=======deploying balance-calculation ==========="

helm upgrade --install -n balance balance-calculation balance-calculation/ -f balance-calculation/value.yaml --create-namespace
