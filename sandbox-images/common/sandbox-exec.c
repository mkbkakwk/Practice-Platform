#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>

int main(int argc, char **argv) {
    if (argc < 3) {
        fputs("sandbox-exec: invalid fixed command\n", stderr);
        return 125;
    }

    int input = open(argv[1], O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (input < 0) {
        perror("sandbox-exec: open stdin");
        return 125;
    }
    if (dup2(input, STDIN_FILENO) < 0) {
        perror("sandbox-exec: dup2 stdin");
        close(input);
        return 125;
    }
    close(input);

    execv(argv[2], &argv[2]);
    errno = errno == 0 ? EIO : errno;
    perror("sandbox-exec: execv");
    return 125;
}
