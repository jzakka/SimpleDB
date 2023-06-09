package com.ll;

import com.ll.converter.ColumnMetaData;
import com.ll.definition.DdlAuto;
import com.ll.exception.PersistenceException;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleDbTest {
    private SimpleDb simpleDb;

    @BeforeAll
    void beforeAll() {
        simpleDb = new SimpleDb("192.168.2.131", "lldj", "lldj123414", "simpleDb__test");
        simpleDb.setDevMode(true);

        createArticleTable();
    }

    private void createArticleTable() {
        simpleDb.run("DROP TABLE IF EXISTS article");

        simpleDb.run("""
                CREATE TABLE article (
                                    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                    PRIMARY KEY(id),
                                    createdDate DATETIME NOT NULL,
                                    modifiedDate DATETIME NOT NULL,
                                    title VARCHAR(100) NOT NULL,
                                    `body` TEXT NOT NULL,
                                    isBlind BIT(1) NOT NULL DEFAULT(0)
                )
                """);
    }

    @BeforeEach
    void beforeEach() {
        truncateArticleTable();
        makeArticleTestData();
    }

    @AfterEach
    void afterEach() {
        createArticleTable();
    }

    private void makeArticleTestData() {
        IntStream.rangeClosed(1, 6).forEach(no -> {
            boolean isBlind = no > 3;
            String title = "제목%d".formatted(no);
            String body = "내용%d".formatted(no);

            simpleDb.run("""
                    INSERT INTO article
                    SET createdDate = NOW(),
                    modifiedDate = NOW(),
                    title = ?,
                    `body` = ?,
                    isBlind = ?
                    """, title, body, isBlind);
        });
    }

    private void truncateArticleTable() {
        simpleDb.run("TRUNCATE article");
    }

    @Test
    public void insert() {
        Sql sql = simpleDb.genSql();
        /*
        == rawSql ==
        INSERT INTO article
        SET createdDate = NOW() ,
        modifiedDate = NOW() ,
        title = '제목 new' ,
        body = '내용 new'
        */
        sql.append("INSERT INTO article")
                .append("SET createdDate = NOW()")
                .append(", modifiedDate = NOW()")
                .append(", title = ?", "제목 new")
                .append(", body = ?", "내용 new");

        long newId = sql.insert(); // AUTO_INCREMENT 에 의해서 생성된 주키 리턴

        assertThat(newId).isGreaterThan(0);
    }

    @Test
    public void update() {
        Sql sql = simpleDb.genSql();

        // id가 0, 1, 2, 3인 글 수정
        // id가 0인 글은 없으니, 실제로는 3개의 글이 삭제됨

        /*
        == rawSql ==
        UPDATE article
        SET title = '제목 new'
        WHERE id IN ('0', '1', '2', '3')
        */
        sql
                .append("UPDATE article")
                .append("SET title = ?", "제목 new")
                .append("WHERE id IN (?, ?, ?, ?)", 0, 1, 2, 3);

        // 수정된 row 개수
        long affectedRowsCount = sql.update();

        assertThat(affectedRowsCount).isEqualTo(3);
    }

    @Test
    public void delete() {
        Sql sql = simpleDb.genSql();

        // id가 0, 1, 3인 글 삭제
        // id가 0인 글은 없으니, 실제로는 2개의 글이 삭제됨
        /*
        == rawSql ==
        DELETE FROM article
        WHERE id IN ('0', '1', '3')
        */
        sql.append("DELETE")
                .append("FROM article")
                .append("WHERE id IN (?, ?, ?)", 0, 1, 3);

        // 삭제된 row 개수
        long affectedRowsCount = sql.delete();

        assertThat(affectedRowsCount).isEqualTo(2);
    }

    @Test
    public void selectDatetime() {
        Sql sql = simpleDb.genSql();
        /*
        == rawSql ==
        SELECT NOW()
        */
        sql.append("SELECT NOW()");

        LocalDateTime datetime = sql.selectDatetime();

        long diff = ChronoUnit.SECONDS.between(datetime, LocalDateTime.now());

        assertThat(diff).isLessThanOrEqualTo(1L);
    }

    @Test
    public void selectLong() {
        Sql sql = simpleDb.genSql();
        /*
        == rawSql ==
        SELECT id
        FROM article
        WHERE id = 1
        */
        sql.append("SELECT id")
                .append("FROM article")
                .append("WHERE id = 1");

        Long id = sql.selectLong();

        assertThat(id).isEqualTo(1);
    }

    @Test
    public void selectString() {
        Sql sql = simpleDb.genSql();
        /*
        == rawSql ==
        SELECT title
        FROM article
        WHERE id = 1
        */
        sql.append("SELECT title")
                .append("FROM article")
                .append("WHERE id = 1");

        String title = sql.selectString();

        assertThat(title).isEqualTo("제목1");
    }

    @Test
    public void selectRow() {
        Sql sql = simpleDb.genSql();
        /*
        == rawSql ==
        SELECT *
        FROM article
        WHERE id = 1
        */
        sql.append("SELECT * FROM article WHERE id = 1");
        Map<String, Object> articleMap = sql.selectRow();

        assertThat(articleMap.get("id")).isEqualTo(1L);
        assertThat(articleMap.get("title")).isEqualTo("제목1");
        assertThat(articleMap.get("body")).isEqualTo("내용1");
        assertThat(articleMap.get("createdDate")).isInstanceOf(LocalDateTime.class);
        assertThat(articleMap.get("modifiedDate")).isInstanceOf(LocalDateTime.class);
        assertThat(articleMap.get("isBlind")).isEqualTo(false);
    }

    @Test
    public void selectArticle() {
        Sql sql = simpleDb.genSql();
        /*
        == rawSql ==
        SELECT * FROM article WHERE id = 1
        */
        sql.append("SELECT * FROM article WHERE id = 1");
        Article article = sql.selectRow(Article.class);

        assertThat(article.getId()).isEqualTo(1L);
        assertThat(article.getTitle()).isEqualTo("제목1");
        assertThat(article.getBody()).isEqualTo("내용1");
        assertThat(article.getCreatedDate()).isNotNull();
        assertThat(article.getModifiedDate()).isNotNull();
        assertThat(article.isBlind()).isFalse();
    }

    @Test
    public void selectArticles() {
        Sql sql = simpleDb.genSql();
        /*
        == rawSql ==
        SELECT *
        FROM article
        ORDER BY id ASC
        LIMIT 3
        */
        sql.append("SELECT * FROM article ORDER BY id ASC LIMIT 3");
        List<Article> articleDtoList = sql.selectRows(Article.class);

        IntStream.range(0, articleDtoList.size()).forEach(i -> {
            long id = i + 1;

            Article articleDto = articleDtoList.get(i);

            assertThat(articleDto.getId()).isEqualTo(id);
            assertThat(articleDto.getTitle()).isEqualTo("제목%d".formatted(id));
            assertThat(articleDto.getBody()).isEqualTo("내용%d".formatted(id));
            assertThat(articleDto.getCreatedDate()).isNotNull();
            assertThat(articleDto.getModifiedDate()).isNotNull();
            assertThat(articleDto.isBlind()).isFalse();
        });
    }

    @Test
    public void selectBind() {
        Sql sql = simpleDb.genSql();
        /*
        == rawSql ==
        SELECT COUNT(*)
        FROM article
        WHERE id BETWEEN '1' AND '3'
        AND title LIKE CONCAT('%', '제목' '%')
        */
        sql.append("SELECT COUNT(*)")
                .append("FROM article")
                .append("WHERE id BETWEEN ? AND ?", 1, 3)
                .append("AND title LIKE CONCAT('%', ? '%')", "제목");

        long count = sql.selectLong();

        assertThat(count).isEqualTo(3);
    }

    @Test
    public void selectIn() {
        Sql sql = simpleDb.genSql();
        /*
        == rawSql ==
        SELECT COUNT(*)
        FROM article
        WHERE id IN ('1','2','3')
        */
        sql.append("SELECT COUNT(*)")
                .append("FROM article")
                .appendIn("WHERE id IN (?)", Arrays.asList(1L, 2L, 3L));

        long count = sql.selectLong();

        assertThat(count).isEqualTo(3);
    }

    @Test
    public void selectOrderByField() {
        List<Long> ids = Arrays.asList(2L, 3L, 1L);

        Sql sql = simpleDb.genSql();
        /*
        SELECT id
        FROM article
        WHERE id IN ('2','3','1')
        ORDER BY FIELD (id, '2','3','1')
        */
        sql.append("SELECT id")
                .append("FROM article")
                .appendIn("WHERE id IN (?)", ids)
                .appendIn("ORDER BY FIELD (id, ?)", ids);

        List<Long> foundIds = sql.selectLongs();

        assertThat(foundIds).isEqualTo(ids);
    }

    @Test
    void 트랜잭션_롤백_테스트() {
        simpleDb.startTransaction();
        simpleDb.run("""
                INSERT INTO article
                    SET createdDate = NOW(),
                    modifiedDate = NOW(),
                    title = "dummyArticle",
                    `body` = "dummyContent",
                    isBlind = 1
                """);
        simpleDb.rollback();
        Sql sql = simpleDb.genSql();
        Long count = sql.append("select count(*)")
                .append("from article")
                .selectLong();

        assertThat(count).isEqualTo(6);
    }

    @Test
    void 트랜잭션_중_예외발생() {
        try {
            simpleDb.startTransaction();
            simpleDb.run("""
                    INSERT INTO article
                        SET createdDate = NOW(),
                        modifiedDate = NOW(),
                        title = "dummyArticle1",
                        `body` = "dummyContent",
                        isBlind = 1
                    """);
            simpleDb.run("""
                    STATEMENT_ERROR
                    """);
            simpleDb.run("""
                    INSERT INTO article
                        SET createdDate = NOW(),
                        modifiedDate = NOW(),
                        title = "dummyArticle3",
                        `body` = "dummyContent",
                        isBlind = 1
                    """);
        } catch (RuntimeException e) {
        }

        Sql sql = simpleDb.genSql();
        Long count = sql.append("select count(*)")
                .append("from article")
                .selectLong();
        assertThat(count).isEqualTo(6);
    }

    @Test
    void 트랜잭션_커밋() {
        simpleDb.startTransaction();
        simpleDb.run("""
                INSERT INTO article
                    SET createdDate = NOW(),
                    modifiedDate = NOW(),
                    title = "dummyArticle1",
                    `body` = "dummyContent",
                    isBlind = 1
                """);
        simpleDb.commit();

        Sql sql = simpleDb.genSql();
        Long count = sql.append("select count(*)")
                .append("from article")
                .selectLong();
        assertThat(count).isEqualTo(7);
    }

    @Test
    void DDL_AUTO_테스트_CREATE() throws PersistenceException {
        simpleDb.setDdlAuto(DdlAuto.CREATE);

        /* DROP TABLE article IF EXISTS;

           CREATE TABLE article(
                id INT UNSIGNED NOT NULL AUTO_INCREMENT,
                PRIMARY KEY(id),
                createdDate DATETIME NOT NULL,
                modifiedDate DATETIME NOT NULL,
                title VARCHAR(100) NOT NULL,
                `body` TEXT NOT NULL,
                isBlind BIT(1) NOT NULL DEFAULT(0)
           )
         */
        simpleDb.definite(Article.class);

        Long count = simpleDb.genSql()
                .append("select count(*)")
                .append("from article")
                .selectLong();

        assertThat(count).isZero();
    }

    @Test
    void DDL_AUTO_테스트_CREATE_DROP() throws PersistenceException {
        simpleDb.setDdlAuto(DdlAuto.CREATE_DROP);

        /* DROP TABLE article IF EXISTS;

           CREATE TABLE article(
                id INT UNSIGNED NOT NULL AUTO_INCREMENT,
                PRIMARY KEY(id),
                createdDate DATETIME NOT NULL,
                modifiedDate DATETIME NOT NULL,
                title VARCHAR(100) NOT NULL,
                `body` TEXT NOT NULL,
                isBlind BIT(1) NOT NULL DEFAULT(0)
           )

           DROP TABLE article IF EXISTS;
         */
        simpleDb.definite(Article.class);

        Map<String, Object> tables = simpleDb.genSql()
                .append("show tables")
                .selectRow();

        assertThat(tables).isEmpty();
    }

    @Test
    void DDL_AUTO_테스트_UPDATE() throws PersistenceException {
        simpleDb.run("""
                ALTER TABLE article
                DROP COLUMN `BODY`;
                """);
        simpleDb.run("""
                ALTER TABLE article
                MODIFY title TEXT;
                """);

        simpleDb.setDdlAuto(DdlAuto.UPDATE);
        simpleDb.definite(Article.class);

        List<String> properties = simpleDb.genSql()
                .append("DESC article")
                .selectRows(ColumnMetaData.class)
                .stream().map(ColumnMetaData::getCOLUMN_NAME)
                .collect(Collectors.toList());

        assertThat(properties).containsExactlyInAnyOrder("id", "createdDate", "modifiedDate", "title", "isBlind", "body");
    }

    @Test
    void DDL_AUTO_테스트_VALIDATE() {
        simpleDb.run("""
                ALTER TABLE article
                DROP COLUMN `BODY`;
                """);

        simpleDb.setDdlAuto(DdlAuto.VALIDATE);
        assertThatThrownBy(()->simpleDb.definite(Article.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessage("엔티티와 테이블의 속성개수가 다릅니다.");
    }

    @Test
    void DDL_AUTO_테스트_NONE() throws PersistenceException {
        simpleDb.setDdlAuto(DdlAuto.NONE);
        simpleDb.definite(Article.class);

        Long count = simpleDb.genSql()
                .append("select count(*)")
                .append("from article")
                .selectLong();

        assertThat(count).isEqualTo(6);
    }
}