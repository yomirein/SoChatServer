# SoChat Server 
<div align="center">

<h2>Make your own messenger server!</h2>

<p>
    <a href="./README-ru.md">Rus</a>
</p>

<h3>Made with Java</h3>
</div>


# About SoChat
__SoChat enables users to create and manage their own messaging servers for text and voice communication through the dedicated SoChat Client application, giving them full control over customization and security.__

[**Made for SoChat Client**](https://github.com/So-Chat/sochat_client)

The main goal of SoChat Server is to make self-hosting a messaging server as simple as possible.

The server is designed for users who want to host their own private messaging infrastructure without having to deal with complicated configuration or deployment procedures.

To host your own server you just need to install Java 23+ and PostgreSQL(SQLite support is planned for a future release) and start SoChatServer.jar. That can be easily downloaded after first preview version

## THIS PROJECT IS STILL WORK IN A PROGRESS
- SoChat is currently under active development. Some features may be incomplete or subject to change.

## Libraries used in project

| Library           | Version     |
|-------------------|-------------|
| Jackson           | 2.20.1      |
| Netty             | 4.2.9 Final |
| JJWT              | 0.11.5      |
| logback-classic   | 1.5.32      |
| HikariCP          | 5.1.0       |
| PostgreSQL        | 42.7.1      |
| SQLite            | 3.53.2.1    |
| Lombok            | 1.18.42     |
| Apache Commons IO | 2.22.0       |

## Build from source
### Requirements
- Maven
- Java 23+
- Git
- PostgreSQL (SQLite support planned)
### Clone the repository
```bash 
git clone https://github.com/So-Chat/SoChatServer.git
cd SoChatServer
```
### Build
```bash
mvn clean package
```
### Result
- After a successful build, the fat JAR will be located in the ```/target``` folder 
- For Example: target/SoChatServer-0.0.1-ALPHA.jar
- Run with 
```bash
java -jar SoChatServer-(version)-(state).jar
```


## License
- **This project is licensed under the [GPL v3.0 License](https://github.com/So-Chat/SoChatServer/blob/master/LICENSE)**
