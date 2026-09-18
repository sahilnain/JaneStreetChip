Here are some pseudo codes for basic protocols that we could implement:

- [UART - 8N1](#uart---8n1)
  - [Transmission](#transmission)
  - [Reception](#reception)
- [I2C](#i2c)
- [SPI](#spi)
  - [SPI Modes Reference](#spi-modes-reference)

# UART - 8N1

## Transmission

```py
constant BAUD_RATE = 9600
constant BIT_PERIOD = 1.0 / BAUD_RATE  # ~104.16 microseconds

function uart_tx_init():
    set_pin_mode(TX_PIN, OUTPUT)
    set_pin(TX_PIN, HIGH)              # UART lines idle high

function uart_send_byte(data_byte):
    # 1. Send START bit
    set_pin(TX_PIN, LOW)
    delay(BIT_PERIOD)

    # Opt. for even parity
    # parity_bit = 0

    # 2. Send 8 Data Bits (LSB first)
    for i in range(8):
        bit_val = (data_byte >> i) & 0x01
        # parity_bit = parity_bit ^ bit_val
        # Can also be computed using a reduction XOR

        set_pin(TX_PIN, bit_val)
        delay(BIT_PERIOD)

    # 3. Optional: Send Parity Bit (if configured)
    # set_pin(TX_PIN, parity_bit)
    # delay(BIT_PERIOD)

    # 4. Send STOP bit
    set_pin(TX_PIN, HIGH)
    delay(BIT_PERIOD)                  # Delay for 1 stop bit
```

## Reception

```python
function uart_rx_init():
    set_pin_mode(RX_PIN, INPUT)

function uart_receive_byte():
    # 1. Wait for falling edge indicating START bit (transition from HIGH to LOW)
    wait_until(read_pin(RX_PIN) == LOW)

    # 2. Center align: delay 0.5 bit period to land in the middle of the START bit
    delay(BIT_PERIOD / 2)

    # Verify line is still LOW (filters out transient noise glitches)
    if read_pin(RX_PIN) != LOW:
        return ERROR_FALSE_START

    received_byte = 0x00

    # 3. Sample 8 data bits at 1 bit intervals (still sampling at the center)
    for i from 0 to 7:
        delay(BIT_PERIOD)
        bit_val = read_pin(RX_PIN)
        received_byte = received_byte | (bit_val << i)

    # 4. Check STOP bit
    delay(BIT_PERIOD)
    stop_bit = read_pin(RX_PIN)

    if stop_bit != HIGH:
        return ERROR_FRAMING           # Stop bit should always be HIGH

    return received_byte
```

# I2C

An I2C bus uses two open-drain bidirectional lines: SDA (Serial Data) and SCL (Serial Clock). Both lines require pull-up resistors and idle HIGH.

In open-drain bit-banging:
- To output 0: Drive pin LOW (output mode, low).
- To output 1 / read: Set pin to HIGH-Z (input mode) so the pull-up resistor pulls it high.

`std_logic` from VHDL could be interesting to use, check if Chisel supports it.

```py
constant I2C_CLOCK_DELAY = 5 microseconds  # Defines SCL half-period (~100 kHz standard mode)

function set_scl(state):
    if state == HIGH:
        set_pin_mode(SCL_PIN, INPUT)       # Release line (pulled HIGH)
        # Optional: Clock stretching check
        while read_pin(SCL_PIN) == LOW:
            pass                           # Wait for slave to release clock
    else:
        set_pin_mode(SCL_PIN, OUTPUT)
        write_pin(SCL_PIN, LOW)

function set_sda(state):
    if state == HIGH:
        set_pin_mode(SDA_PIN, INPUT)       # Release line (pulled HIGH)
    else:
        set_pin_mode(SDA_PIN, OUTPUT)
        write_pin(SDA_PIN, LOW)

function read_sda():
    set_pin_mode(SDA_PIN, INPUT)           # Ensure floating / input
    return read_pin(SDA_PIN)
```

From start stop restart

```py
function i2c_start():
    set_sda(HIGH)
    set_scl(HIGH)
    delay(I2C_CLOCK_DELAY)

    set_sda(LOW)                           # SDA drops while SCL is HIGH
    delay(I2C_CLOCK_DELAY)
    set_scl(LOW)                           # Clamp clock low to begin data transfer

function i2c_stop():
    set_sda(LOW)
    delay(I2C_CLOCK_DELAY)
    set_scl(HIGH)                          # SCL goes HIGH first
    delay(I2C_CLOCK_DELAY)
    set_sda(HIGH)                          # SDA rises while SCL is HIGH
    delay(I2C_CLOCK_DELAY)
```

Read and write

```python
# Returns TRUE if Slave acknowledged (ACK = 0), FALSE if NACK
function i2c_write_byte(byte_val):
    for i from 7 down to 0:
        bit_val = (byte_val >> i) & 0x01
        set_sda(bit_val)
        delay(I2C_CLOCK_DELAY)

        set_scl(HIGH)                      # Slave reads bit here
        delay(I2C_CLOCK_DELAY)
        set_scl(LOW)

    # 9th clock cycle: Read ACK/NACK from slave
    set_sda(HIGH)                          # Release SDA for slave response
    delay(I2C_CLOCK_DELAY)
    set_scl(HIGH)

    ack_bit = read_sda()                   # 0 = ACK, 1 = NACK
    delay(I2C_CLOCK_DELAY)
    set_scl(LOW)

    return (ack_bit == 0)

# ack_response: send ACK (0) to request more bytes, or NACK (1) on final byte
function i2c_read_byte(ack_response):
    received_byte = 0x00
    set_sda(HIGH)                          # Release SDA for slave to drive

    for i from 7 down to 0:
        delay(I2C_CLOCK_DELAY)
        set_scl(HIGH)
        bit_val = read_sda()               # Master samples bit
        received_byte = received_byte | (bit_val << i)
        delay(I2C_CLOCK_DELAY)
        set_scl(LOW)

    # 9th clock cycle: Master sends ACK / NACK to slave
    set_sda(ack_response)                  # 0 = ACK, 1 = NACK
    delay(I2C_CLOCK_DELAY)
    set_scl(HIGH)
    delay(I2C_CLOCK_DELAY)
    set_scl(LOW)
    set_sda(HIGH)                          # Release SDA

    return received_byte
```

Transaction flow

```py
constant I2C_WRITE = 0
constant I2C_READ  = 1
constant ACK  = 0
constant NACK = 1

function i2c_write_register(device_addr_7bit, reg_addr, data_val):
    i2c_start()

    # Address byte: 7 bits address + 1 bit R/W flag
    addr_byte = (device_addr_7bit << 1) | I2C_WRITE
    if not i2c_write_byte(addr_byte):
        i2c_stop()
        return ERROR_NO_DEVICE

    if not i2c_write_byte(reg_addr):
        i2c_stop()
        return ERROR_REG_NACK

    if not i2c_write_byte(data_val):
        i2c_stop()
        return ERROR_DATA_NACK

    i2c_stop()
    return SUCCESS
```

# SPI

SPI (Serial Peripheral Interface) is a synchronous, full-duplex, four-wire protocol:

| Signal  | Role                                                                 |
| ------- | -------------------------------------------------------------------- |
| SCLK    | Driven by the Master.                                                |
| MOSI    | Data line from Master to Slave.                                      |
| MISO    | Data line from Slave to Master.                                      |
| CS / SS | Active-low signal driven by Master to address a specific peripheral. |

Unlike I2C, SPI transmits and receives simultaneously on every clock cycle. The implementation below demonstrates standard SPI Mode 0 (CPOL = 0, CPHA = 0: clock idles LOW, data sampled on the rising edge, shifted out on the falling edge), MSB first.

init

```py
constant SPI_HALF_PERIOD = 1 microsecond  # Clock half-period (e.g., 500 kHz SCLK)

function spi_init():
    set_pin_mode(MOSI_PIN, OUTPUT)
    set_pin_mode(SCLK_PIN, OUTPUT)
    set_pin_mode(CS_PIN, OUTPUT)
    set_pin_mode(MISO_PIN, INPUT)

    write_pin(CS_PIN, HIGH)              # CS is active-low; idle HIGH
    write_pin(SCLK_PIN, LOW)             # Mode 0: CPOL=0 (clock idles LOW)
    write_pin(MOSI_PIN, LOW)
```

Full Duplex

```py
function spi_transfer_byte(tx_byte):
    rx_byte = 0x00

    for i from 7 down to 0:
        # 1. Setup Phase: Set MOSI before the sampling clock edge (MSB first)
        mosi_bit = (tx_byte >> i) & 0x01
        write_pin(MOSI_PIN, mosi_bit)
        delay(SPI_HALF_PERIOD)

        # 2. Leading Edge (Rising): Clock goes HIGH; sample MISO
        write_pin(SCLK_PIN, HIGH)
        miso_bit = read_pin(MISO_PIN)
        rx_byte = (rx_byte << 1) | miso_bit
        delay(SPI_HALF_PERIOD)

        # 3. Trailing Edge (Falling): Clock returns to idle (LOW)
        write_pin(SCLK_PIN, LOW)

    return rx_byte
```

Read write block

```py
function spi_transaction(tx_buffer, rx_buffer, length):
    write_pin(CS_PIN, LOW)               # Assert slave select
    delay(SPI_HALF_PERIOD)               # CS setup time

    for i from 0 to length - 1:
        rx_buffer[i] = spi_transfer_byte(tx_buffer[i])

    delay(SPI_HALF_PERIOD)               # CS hold time
    write_pin(CS_PIN, HIGH)              # Deassert slave select
```

## SPI Modes Reference

| Mode | CPOL (Idle Clock) | CPHA (Clock Phase) | Data Sampled On       | Data Shifted On       |
| ---- | ----------------- | ------------------ | --------------------- | --------------------- |
| 0    | 0 (Low)           | 0                  | First edge (Rising)   | Second edge (Falling) |
| 1    | 0 (Low)           | 1                  | Second edge (Falling) | First edge (Rising)   |
| 2    | 1 (High)          | 0                  | First edge (Falling)  | Second edge (Rising)  |
| 3    | 1 (High)          | 1                  | Second edge (Rising)  | First edge (Falling)  |