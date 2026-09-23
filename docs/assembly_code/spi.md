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

# SPI Transfer (Mode 0: CPOL=0, CPHA=0, MSB first)

Byte to send in `R0`, received byte accumulates in `R1`.

1. The program here does not take into account multi-slave for now. TODO: add multi-slave feature. Should be easy, 
CS bit, we can set a register that control IO pins.
2. If we are able to alter the clock frequency of the emulator, to match 
the SPI clock rate, then we can get away with clk_rise in this program.
3. Need to adapt this if the emulator is a slave.

```
        SET_PIN CS, 0
        LOAD    R2, 8

spi_loop:
        ; --- write bit on MOSI before clock rises ---
        LOAD    R3, R0         ; might be redundant, can save the result of AND (next instruction) in another register
        AND     R3, 0x80       ; isolate MSB
        BZ      R3, mosi_low
        SET_PIN MOSI, 1
        JMP     clk_rise
mosi_low:
        SET_PIN MOSI, 0

clk_rise:
        SET_PIN SCLK, 1
        READ_PIN MISO, R4      ; sample peripheral's bit
        SHR     R1             ; make room in RX byte ; I dont think this is correct, we will lose the bits in R1, we can set a register to zero at the start of the program and then keep shifting it left.
        OR      R1, R4         ; drop R4's bit into R1

        SET_PIN SCLK, 0
        SHR     R0             ; shift TX byte for next bit
        DEC     R2
        BNZ     R2, spi_loop

        SET_PIN CS, 1
```

Modes 1–3 use the same skeleton — only the placement of `READ_PIN`/bit-write
relative to `SET_PIN SCLK` changes (which edge you sample vs. shift on).
