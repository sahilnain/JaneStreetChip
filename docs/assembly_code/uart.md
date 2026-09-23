# Instruction Set

```
Basic instructions:
LOAD    Rd, imm        ; Rd = imm
DEC     Rd             ; Rd = Rd - 1
SHR     Rd             ; shift Rd right by 1, LSB goes into carry bit C
AND     Rd, imm        ; Rd = Rd & imm
OR      Rd, Rs         ; Rd = Rd | Rs
BZ   Rd, label         ; branch if Rd == 0
BNZ  Rd, label         ; branch if Rd != 0
BC   label             ; branch if carry bit set
JMP  label
CALL label
RET

Protocol instructions:
SET_PIN pin, val       ; drive pin to 0 or 1
READ_PIN pin, Rd       ; sample pin into Rd
RELEASE_PIN pin        ; stop driving (open-drain — let pull-up/down decide)

```

# UART TX (8N1, LSB first)
Program for 1 burst of data, 8 data bits + 1 strat bit

1. Byte to send in `R0`. Bit-period delay count in `DELAY`.
2. Tx 8 data bits per packet.
3. If we are able to alter the clock frequency of the emulator, to match 
the baud rate, then we can get away with bit_delay in this program.

```
        LOAD    R2, 8          ; bit counter
        SET_PIN TX, 0          ; start bit
        CALL    bit_delay

tx_loop:
        SHR     R0             ; LSB -> carry
        BC      set_high
        SET_PIN TX, 0
        JMP     hold
set_high:
        SET_PIN TX, 1
hold:
        CALL    bit_delay
        DEC     R2
        BNZ     R2, tx_loop

        SET_PIN TX, 1          ; stop bit
        CALL    bit_delay

bit_delay:                     ; busy-wait one bit period
        LOAD    R3, DELAY
delay_loop:
        DEC     R3
        BNZ     R3, delay_loop
        RET
```
