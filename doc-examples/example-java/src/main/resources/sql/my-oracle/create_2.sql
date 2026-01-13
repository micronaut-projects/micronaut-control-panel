create table EMP
(
    EMPNO    NUMBER(4) not null
        constraint PK_EMP
            primary key,
    ENAME    VARCHAR2(10),
    JOB      VARCHAR2(9),
    MGR      NUMBER(4)
        constraint FK_EMPNO
            references EMP,
    HIREDATE DATE,
    SAL      NUMBER(7, 2),
    COMM     NUMBER(7, 2),
    DEPTNO   NUMBER(2)
        constraint FK_DEPTNO
            references DEPT
)
