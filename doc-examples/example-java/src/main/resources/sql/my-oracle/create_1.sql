create table DEPT
(
    DEPTNO NUMBER(2) not null
        constraint PK_DEPT
            primary key,
    DNAME  VARCHAR2(14),
    LOC    VARCHAR2(13)
)
