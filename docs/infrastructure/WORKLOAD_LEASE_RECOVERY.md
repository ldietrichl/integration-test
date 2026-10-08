# Восстановление после оборванного запуска

Чужой lease не снимается автоматически. Сначала убедитесь, что прежний процесс
завершён; затем сохраните read-only план для нужного Deployment и ConfigMap.
Общие команды сохранили исторический префикс explab2885; target задаётся параметрами.

```powershell
.\gradlew.bat explab2885InspectLease -PrecoveryDeployment=splitter-mapper-service -PrecoveryConfigMaps=splitter-mapper-service,splitter-mapper-service-lib -PrecoveryPlan=.workload-recovery/mapper-review.json
```

Проверьте target, UID, владельца и resourceVersion в плане. После подтверждения
остановки старого процесса выполните отдельно:

```powershell
.\gradlew.bat explab2885ReleaseLease -PrecoveryDeployment=splitter-mapper-service -PrecoveryConfigMaps=splitter-mapper-service,splitter-mapper-service-lib -PrecoveryPlan=.workload-recovery/mapper-review.json -PrecoveryConfirmStopped=true
```

Release нельзя объединять с inspect или тестами. Неизвестный либо изменившийся
lease требует новой проверки и нового плана. Автоматического force-снятия нет.
Снятие lease не восстанавливает настройки до аварии. Для восстановления нужны
подтверждённые preimage из `.workload-recovery/private`; неизвестные значения не угадывают.

Локальная проверка защит: `.\gradlew.bat workloadLeaseRecoveryCheck`.
