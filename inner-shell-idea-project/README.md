# Inner Shell Generator

Десктопное Java-приложение для построения внутренней поверхности PLY-модели с равномерным отступом от внешней оболочки.

## Что умеет

- открывать `.ply` в форматах `ascii` и `binary_little_endian`;
- строить внутреннюю оболочку по нормалям, а не простым масштабированием;
- сохранять результат снова в `.ply`;
- при необходимости замыкать оболочку по открытым краям;
- работать как через GUI, так и через консоль.

## Как открыть в IntelliJ IDEA

1. Откройте папку проекта `inner-shell-idea-project` через **File -> Open**.
2. IDEA увидит `pom.xml` и импортирует проект как Maven-проект.
3. Укажите JDK **21** или новее.
4. Откройте класс `src/main/java/app/InnerShellApp.java`.
5. Запустите `main()`.

Никаких внешних библиотек в проекте нет — используется только стандартный JDK.

## Быстрый запуск без Maven

### Windows

Запустить GUI:

```bat
run.bat
```

Запустить консольный режим:

```bat
run.bat --input demo\Untitled.ply --output demo\result_cli.ply --offset 0.1
```

### Linux / macOS

Сделайте файл исполняемым один раз:

```bash
chmod +x run.sh
```

Запустить GUI:

```bash
./run.sh
```

Запустить консольный режим:

```bash
./run.sh --input demo/Untitled.ply --output demo/result_cli.ply --offset 0.1
```

## Пример консольного запуска через IDEA terminal

```bash
mvn -q -DskipTests package
java -jar target/inner-shell-generator-1.0.0.jar --input demo/Untitled.ply --output demo/result_cli.ply --offset 0.1
```

## Ограничения алгоритма

Алгоритм делает offset по нормалям поверхности. Это намного корректнее, чем обычное масштабирование, но для очень сложных моделей, узких участков и слишком большого отступа возможны самопересечения. Для тяжёлых промышленных кейсов следующим шагом обычно делают offset через SDF/voxel/level set.
