package com.example.hibernatefun

import org.hibernate.dialect.PostgreSQL10Dialect
import org.hibernate.dialect.function.SQLFunctionTemplate
import org.hibernate.type.StandardBasicTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.PostgreSQLContainer.DEFAULT_TAG
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.persistence.CascadeType
import javax.persistence.Entity
import javax.persistence.EntityManager
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.ManyToMany

@SpringBootTest
@Testcontainers
class HibernateFunApplicationTests {

    companion object {
        @Container
        private val container = PostgreSQLContainer<Nothing>("postgres:12.4-alpine").apply {
            println(portBindings)
            withDatabaseName("example")
            withUsername("user")
            withPassword("password")
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.password", container::getPassword);
            registry.add("spring.datasource.username", container::getUsername);
        }

    }

    @Autowired
    private lateinit var repository: StudentRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `students with courses are sorted`() {
        val school = this
        school add StudentEntity(
            name = "Alice",
            courses = mutableListOf(
                CourseEntity(name = "Warehouses"),
                CourseEntity(name = "Rockets"),
                CourseEntity(name = "Airplanes"),
            )
        )
        school add StudentEntity(
            name = "Bobby",
            courses = mutableListOf(
                CourseEntity(name = "Gyms"),
                CourseEntity(name = "Airplanes"),
                CourseEntity(name = "Xeroxes"),
            )
        )

        assertEquals("Bobby", repository.findAll(Sort.by(Sort.Direction.DESC, "name"))[0].name)
        val aggregatedSorting = repository.aggregatedSorting(Sort.by(Sort.Direction.DESC, "courseNames"))
        aggregatedSorting.forEach {
            println(" | ${it.studentEntity.name} | ${it.courseNames} ")
        }
        assertEquals("Alice", aggregatedSorting[0].studentEntity.name)
    }

    infix fun add(studentEntity: StudentEntity) = repository.save(studentEntity)
}

@Entity(name = "students")
class StudentEntity(
    @field:Id
    @field:GeneratedValue
    var id: Long = 0,

    var name: String,

    @ManyToMany(cascade = [CascadeType.PERSIST])
    var courses: MutableList<CourseEntity> = mutableListOf()
)

@Entity(name = "courses")
class CourseEntity(
    @field:Id
    @field:GeneratedValue
    var id: Long = 0,

    var name: String,
)

@Repository
interface StudentRepository : JpaRepository<StudentEntity, Long> {
    @Query(
        """
        select student as studentEntity, string_agg(course.name, ', ', course.name) as courseNames
        from students student
            left join student.courses course
        group by student.id
        """
    )
    fun aggregatedSorting(sort: Sort): List<StudentEntityProjection>
}

interface StudentEntityProjection {
    val studentEntity: StudentEntity
    val courseNames: String
}

class PostgresRichDialect : PostgreSQL10Dialect() {
    init {
        registerFunction("string_agg", SQLFunctionTemplate(StandardBasicTypes.STRING, "string_agg(?1, ?2)"))
        registerFunction(
            "string_agg",
            SQLFunctionTemplate(
                StandardBasicTypes.STRING,
                "string_agg(?1, ?2 ORDER BY ?3 )"
            )
        )

    }
}