# Planning

- [Planning](#planning)
  - [Summary](#summary)
  - [Hard requirements](#hard-requirements)
    - [Protocols](#protocols)
      - [Basic](#basic)
      - [Medium](#medium)
      - [Advanced](#advanced)
    - [Timing](#timing)
    - [Physical pins](#physical-pins)
    - [Gate estimation - AI](#gate-estimation---ai)
- [Designing](#designing)
  - [ISA](#isa)
    - [Opcode - Classes](#opcode---classes)

## Summary

| Category    |       Value        |
| ----------- | :----------------: |
| Technology  | IHP's 130nm CMOS5L |
| Design Tech |     6x4 tiles      |
| Area        |   ~24,000 cells    |
| Clock speed |       50 MHz       |
| IO          |     8 IO pins      |
:Hard requirements

| Category | Value |
| -------- | :---: |
|          |       |
:Design choices

## Hard requirements

We got at least 6x4 tiles, with 1000 logic cells per tile. This means 24000 logic cells in total. We also need to consider the area occupied by SRAM. We can use the SRAM for instructions and data (as in a regular CPU). 

> Discussion
>
> I think we could use some asymmetric size cache? 

### Protocols

#### Basic

| Name | Wires                 | Min Baud rates | Target Baud rates | Max Baud rates | Notes                                    |
| ---- | --------------------- | -------------- | ----------------- | -------------- | ---------------------------------------- |
| UART | 3 (TX,RX,GND)         | 110            | 115200            | 2e6            | 8 clock cycles per byte, 10 bit per byte |
| SPI  | 4 (MOSI,MISO,SCLK,CS) | 100            | 10e6              | 100e6          | Master/Slave, multiple Slave             |
| I2C  | 3 (SDA,SCL,GND)       | 100            | 1e6               | 5e6            | Multi-master/Multi-slave                 |

#### Medium


| Name             | Wires                                               | Min Baud rates | Target Baud rates | Max Baud rates | Notes                    |
| ---------------- | --------------------------------------------------- | -------------- | ----------------- | -------------- | ------------------------ |
| low-speed USB    | 4 (D+, D-, GND, VBUS)                               | 1.5k           | 12k               | 1.5e6          |                          |
| 10 Mbit Ethernet | 2 (TX+, TX-, RX+, RX-, GND, MDC, MDIO) for 10base-T | 20e6           | 20e6              | 20e6           | With Manchester encoding |

#### Advanced

| Name | Wires                    | Min Baud rates | Target Baud rates | Max Baud rates | Notes |
| ---- | ------------------------ | -------------- | ----------------- | -------------- | ----- |
| JTAG | 5 (TCK,TDI,TDO,TMS,TRST) | 10e6           | 20e6              | 50e6           |       |
| SWD  | 2 (SWDIO,SWCLK)          | 10e6           | 20e6              | 50e6           |       |
| PS/2 | 3 (CLK,DATA,GND)         | 10e3           | 10e3              | 16.7e3         |       |
| CAN  | 2 (CANH,CANL)            | 1e6            | 5e6               | 8e6            |       |


### Timing

Looking at the charts above, we should aim for 50-100 MHz clock speed. We need to oversample to be able to load and handle data on the interface. We will support **50 MHz** as it is the clock driven by the RP2040 board.

### Physical pins

With the Tiny Tapeout possibilities, we could have a **8 IO pin** crossbar which should be more than enough for our protocols.


### Gate estimation - AI

Estimated Gate Budget (24 Tiles $\approx$ 24,000 Cells)

- Program Memory (SRAM vs. Synthesized DFF):
  - If using OpenRAM (if accessible in the flow): ~1k gates overhead.
  - If synthesized standard-cell flip-flops (128 words $\times$ 16-bit): ~6,500 cells.
- Core Execution Unit (ALU, Program Counter, Delay timers): ~2,000 cells.
- TX / RX FIFOs (2 $\times$ 8 bytes deep): ~1,800 cells.
- USB Assist Unit (NRZI, bit-stuffing detection, sync pattern match): ~1,200 cells.
- Pin Multiplexer, Glitch Filters & Tri-state Controller: ~1,500 cells.
- Top-level Control / Host Interface (SPI/Wishbone slave for TT): ~1,200 cells.

Total Estimated Footprint: ~14,200 cells (~60% utilization of your 24-tile budget, leaving healthy headroom for routing and timing closure).

# Designing

## ISA

We need to define a proper ISA to be able to control the chip. The most straightforward ISA would be one where we would simply toggle manually all the pins with a 8-bit payload for the 8 possible pins. However, we could also define a more complex ISA with a few instructions. The ISA would be loaded in the SRAM and we would have a controller that would execute the instructions. 

For this, a 16-bit ISA should be enough as we need to balance flexibility and die area.

```
+------------------------------------------------+
| opcode [3] |     operand (12-bit)              |
+------------------------------------------------+
15         13 12                                 0
```

### Opcode - Classes

- `0b000`: NOP
- `0b001`: SET_PINS