from typing import TypeVar, Callable

T = TypeVar('T')
U = TypeVar('U')
V = TypeVar('V')


class FunctionUtils:

    @staticmethod
    def combine_callables(callable1: Callable[[T], U],
                          callable2: Callable[[U], V]) \
            -> Callable[[T], V]:
        return lambda x: callable2(callable1(x))
