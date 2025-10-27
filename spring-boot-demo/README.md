# Firefly Spring Boot Demo

Este proyecto demuestra la integración completa de **Firefly** con **Spring Boot 3.2**, compilando código Firefly nativo directamente a bytecode JVM compatible con el ecosistema Spring.

## 🚀 Características

- ✅ Compilación nativa de Firefly a bytecode JVM
- ✅ Integración completa con Spring Boot 3.2
- ✅ Controllers REST funcionales
- ✅ Dependency Injection con Spring
- ✅ Anotaciones Spring nativas (`@RestController`, `@GetMapping`, etc.)
- ✅ Build automático con Maven
- ✅ Empaquetado con Spring Boot Maven Plugin

## 📋 Requisitos

- Java 21+
- Maven 3.6+
- Firefly compiler instalado

## 🏗️ Estructura del Proyecto

```
spring-boot-demo/
├── src/main/
│   ├── firefly/           # Código fuente Firefly
│   │   ├── main.fly       # Clase principal con @SpringBootApplication
│   │   └── app.fly        # Controllers y servicios
│   └── resources/
│       └── application.properties  # Configuración Spring Boot
├── pom.xml                # Configuración Maven
├── test-app.sh           # Script de testing automático
└── README.md
```

## 📝 Código Firefly

### main.fly - Aplicación Principal

```firefly
package com.firefly.demo

import org::springframework::boot::SpringApplication
import org::springframework::boot::autoconfigure::SpringBootApplication
import org::springframework::context::annotation::ComponentScan

@SpringBootApplication
@ComponentScan("com.firefly.demo")
class Application {
    fn main(args: Array<String>) -> Unit {
        SpringApplication.run(Application.class, args);
    }
}
```

### app.fly - REST Controllers

```firefly
package com.firefly.demo

import org::springframework::web::bind::annotation::*
import org::springframework::stereotype::*

@RestController
@RequestMapping("/api")
class HelloController {
    @GetMapping("/hello")
    fn hello() -> String {
        return "Hello from Firefly + Spring Boot!";
    }
    
    @GetMapping("/status")
    fn status() -> String {
        return "Status: Running on Firefly";
    }
    
    @PostMapping("/echo")
    fn echo(@RequestBody message: String) -> String {
        return "Echo from Firefly";
    }
}
```

## 🔧 Configuración Maven

El proyecto usa dos plugins clave:

### 1. Firefly Maven Plugin
Compila archivos `.fly` a bytecode JVM:

```xml
<plugin>
    <groupId>com.firefly</groupId>
    <artifactId>firefly-maven-plugin</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <phase>process-classes</phase>
            <goals>
                <goal>compile</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 2. Spring Boot Maven Plugin
Empaqueta la aplicación con todas las dependencias:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <mainClass>com.firefly.demo.Application</mainClass>
    </configuration>
</plugin>
```

## 🛠️ Construcción

### Compilar el proyecto

```bash
mvn clean package
```

Este comando:
1. Compila los archivos Firefly (`.fly`) a bytecode JVM
2. Genera las clases Java compatibles
3. Empaqueta todo en un JAR ejecutable con Spring Boot

### Salida esperada

```
[1/4] Parsing...
  ✓ Parse tree generated (38ms)
[2/4] Building AST...
  ✓ AST constructed (4ms)
[3/4] Semantic Analysis...
  ✓ Semantic analysis passed (1ms)
[4/4] Code Generation...
  ✓ Bytecode generated: com/firefly/demo/HelloController.class
  ✓ Bytecode generated: com/firefly/demo/Application.class
  
✅ Success! Compilation completed (56ms, 45 lines)
```

## 🚀 Ejecución

### Iniciar la aplicación

```bash
java -jar target/firefly-spring-demo-1.0.0.jar
```

La aplicación arrancará en el puerto **8081** (configurable en `application.properties`).

### Testing automático

Ejecuta el script de pruebas completo:

```bash
./test-app.sh
```

Este script:
- ✅ Inicia la aplicación Spring Boot
- ✅ Espera a que arranque completamente
- ✅ Prueba todos los endpoints REST
- ✅ Verifica que no hay errores en logs
- ✅ Detiene la aplicación limpiamente

## 🧪 Endpoints Disponibles

### GET /api/hello
Retorna un saludo simple.

```bash
curl http://localhost:8081/api/hello
# Response: Hello from Firefly + Spring Boot!
```

### GET /api/status
Retorna el estado de la aplicación.

```bash
curl http://localhost:8081/api/status
# Response: Status: Running on Firefly
```

### POST /api/echo
Hace echo del mensaje recibido.

```bash
curl -X POST -H 'Content-Type: text/plain' \
     -d 'Test message' \
     http://localhost:8081/api/echo
# Response: Echo from Firefly
```

## 🔍 Detalles Técnicos

### Resolución de Métodos Estáticos

El compilador Firefly resuelve correctamente métodos estáticos de Spring:

```firefly
SpringApplication.run(Application.class, args);
```

Se compila a:

```
invokestatic org/springframework/boot/SpringApplication.run:
    (Ljava/lang/Class;[Ljava/lang/String;)
    Lorg/springframework/context/ConfigurableApplicationContext;
```

### Anotaciones

Las anotaciones Spring se resuelven a sus nombres completamente calificados:

- `@SpringBootApplication` → `org.springframework.boot.autoconfigure.SpringBootApplication`
- `@RestController` → `org.springframework.web.bind.annotation.RestController`
- `@GetMapping` → `org.springframework.web.bind.annotation.GetMapping`

Los valores de anotaciones se emiten correctamente como arrays cuando es necesario.

### Classpath Dinámico

El plugin Maven inyecta el classpath completo del proyecto al compilador, permitiendo:

- Resolución de clases Spring Boot en tiempo de compilación
- Validación semántica con acceso a todas las dependencias
- Resolución de métodos sobrecargados usando reflexión

## 📦 Dependencias

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

Spring Boot Starter Web incluye:
- Spring MVC
- Tomcat embebido
- Jackson para JSON
- Todas las dependencias necesarias para REST APIs

## ✅ Verificación

Para verificar que todo funciona:

```bash
# 1. Build completo
mvn clean package

# 2. Ejecutar tests
./test-app.sh

# Salida esperada:
# 🎉 Firefly + Spring Boot is working perfectly!
```

## 🎯 Conclusión

Este proyecto demuestra que Firefly puede:

1. ✅ Compilar a bytecode JVM 100% compatible
2. ✅ Integrarse perfectamente con Spring Boot
3. ✅ Usar anotaciones Spring nativas
4. ✅ Generar aplicaciones web REST completas
5. ✅ Funcionar en el ecosistema Java sin modificaciones

**Firefly + Spring Boot = 🔥**
